package vn.vnpost.cdp.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.entity.ProfileIdentityLink;
import vn.vnpost.cdp.profile.enums.IdentityType;
import vn.vnpost.cdp.profile.repository.MasterProfileRepository;
import vn.vnpost.cdp.profile.repository.ProfileIdentityLinkRepository;
import vn.vnpost.cdp.profile.service.match.IdentityMatchThresholds;

import java.util.*;

@Slf4j
@Service
public class ProfileMatchingService {

    private static final short PROFILE_MERGED  = 3;
    private static final short PROFILE_DELETED = 5;

    /** Một giá trị khóa khớp nhiều hơn ngưỡng này thì coi như khóa rác, không dùng để tìm candidate. */
    private static final int MAX_PROFILES_PER_KEY = IdentityMatchThresholds.MAX_PROFILES_PER_KEY;

    private final MasterProfileRepository masterProfileRepository;
    private final ProfileIdentityLinkRepository identityLinkRepository;

    public ProfileMatchingService(MasterProfileRepository masterProfileRepository,
                                  ProfileIdentityLinkRepository identityLinkRepository) {
        this.masterProfileRepository = masterProfileRepository;
        this.identityLinkRepository = identityLinkRepository;
    }

    public Mono<List<MasterProfile>> findCandidateProfiles(NormalizedProfileData data) {
        List<Flux<MasterProfile>> steps = new ArrayList<>();

        // 1. Match by identityNo
        if (StringUtils.hasText(data.getIdentityNo())) {
            steps.add(byProfileKey(masterProfileRepository.findByIdentityNo(data.getIdentityNo()), "CCCD"));
        }

        // 2. Match by phone — master_profiles and identity_links
        if (StringUtils.hasText(data.getPhone())) {
            steps.add(byProfileKey(masterProfileRepository.findByPhone(data.getPhone()), "SĐT"));
            steps.add(byLinks(identityLinkRepository
                    .findByIdentityTypeAndIdentityValue("PHONE", data.getPhone()), "PHONE"));
        }

        // 3. Match by email — master_profiles and identity_links
        if (StringUtils.hasText(data.getEmail())) {
            steps.add(byProfileKey(masterProfileRepository.findByEmail(data.getEmail()), "email"));
            steps.add(byLinks(identityLinkRepository
                    .findByIdentityTypeAndIdentityValue("EMAIL", data.getEmail()), "EMAIL"));
        }

        // 4. Match by sourceSystem + sourceCustomerId in identity_links
        if (StringUtils.hasText(data.getSourceSystem()) && StringUtils.hasText(data.getSourceCustomerId())) {
            steps.add(byLinks(identityLinkRepository.findBySourceSystemAndSourceCustomerId(
                    data.getSourceSystem(), data.getSourceCustomerId()), "sourceCustomerId"));
        }

        // 5. Match by taxCode (MST) — khóa doanh nghiệp duy nhất
        if (StringUtils.hasText(data.getTaxCode())) {
            steps.add(byProfileKey(masterProfileRepository.findByTaxCode(data.getTaxCode()), "MST"));
        }

        // 6. Match theo các identity_type mạnh, xuyên nguồn (KHÔNG gồm DEVICE_ID/COOKIE_ID —
        //    dành cho Probabilistic Matching sau này, tránh false-positive khi auto-merge).
        Map<IdentityType, String> typedIdentifiers = new LinkedHashMap<>();
        typedIdentifiers.put(IdentityType.KHL_CODE, data.getKhlCode());
        typedIdentifiers.put(IdentityType.CRM_ID, data.getCrmId());
        typedIdentifiers.put(IdentityType.POST_ID, data.getPostId());
        typedIdentifiers.put(IdentityType.APP_USER_ID, data.getAppUserId());
        typedIdentifiers.put(IdentityType.PAYMENT_ID, data.getPaymentId());
        for (Map.Entry<IdentityType, String> entry : typedIdentifiers.entrySet()) {
            if (!StringUtils.hasText(entry.getValue())) continue;
            steps.add(byLinks(identityLinkRepository
                    .findByIdentityTypeAndIdentityValue(entry.getKey().name(), entry.getValue()),
                    entry.getKey().name()));
        }

        return Flux.concat(steps)
                .collect(LinkedHashMap<Long, MasterProfile>::new, (map, p) -> map.putIfAbsent(p.getId(), p))
                .map(map -> {
                    List<MasterProfile> candidates = new ArrayList<>(map.values());
                    log.info("ProfileMatchingService - found {} candidate(s) for sourceSystem={} sourceCustomerId={}",
                            candidates.size(), data.getSourceSystem(), data.getSourceCustomerId());
                    return candidates;
                });
    }

    /**
     * Các hồ sơ còn dùng được tìm theo một khóa của {@code master_profiles}.
     *
     * <p>Một giá trị khóa khớp quá nhiều hồ sơ thì không còn tính phân biệt (SĐT rác / hotline
     * shipper / "0000000000"). Nhận hết sẽ đẩy mọi record vào nhánh CONFLICT với hàng nghìn candidate,
     * nên bỏ qua khóa đó và ghi log để đội dữ liệu xử lý.
     */
    private Flux<MasterProfile> byProfileKey(Flux<MasterProfile> found, String keyLabel) {
        return found.collectList().flatMapMany(list -> {
            if (list.size() > MAX_PROFILES_PER_KEY) {
                log.warn("ProfileMatchingService - {} hồ sơ cùng {} (> {}) → khóa không có tính phân biệt, bỏ qua",
                        list.size(), keyLabel, MAX_PROFILES_PER_KEY);
                return Flux.empty();
            }
            return Flux.fromIterable(list).filter(this::isUsable);
        });
    }

    /** Như {@link #byProfileKey} nhưng đi từ identity_links: chỉ nhận link đang ACTIVE (status=1). */
    private Flux<MasterProfile> byLinks(Flux<ProfileIdentityLink> links, String keyLabel) {
        return links
                .filter(l -> l.getStatus() != null && l.getStatus() == 1)
                .map(ProfileIdentityLink::getMasterProfileId)
                .distinct()
                .collectList()
                .flatMapMany(profileIds -> {
                    if (profileIds.size() > MAX_PROFILES_PER_KEY) {
                        log.warn("ProfileMatchingService - {} hồ sơ cùng link {} (> {}) → khóa không có tính "
                                        + "phân biệt, bỏ qua",
                                profileIds.size(), keyLabel, MAX_PROFILES_PER_KEY);
                        return Flux.empty();
                    }
                    return Flux.fromIterable(profileIds)
                            .concatMap(masterProfileRepository::findById)
                            .filter(this::isUsable);
                });
    }

    /**
     * Hồ sơ đã MERGED/DELETED là "bia mộ" — nó đã trỏ mergedIntoProfileId sang hồ sơ khác. Nếu để
     * lọt vào pool thì AUTO_MERGE sẽ ghi dữ liệu vào hồ sơ chết và dữ liệu đó không còn hiển thị ở
     * bất kỳ đâu. Nhất quán với loadActiveProfile() của luồng admin (chỉ chặn 3 và 5).
     */
    private boolean isUsable(MasterProfile p) {
        Short status = p.getStatus();
        return status == null || (status != PROFILE_MERGED && status != PROFILE_DELETED);
    }
}
