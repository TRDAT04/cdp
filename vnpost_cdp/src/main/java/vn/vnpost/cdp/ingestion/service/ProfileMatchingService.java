package vn.vnpost.cdp.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.entity.ProfileIdentityLink;
import vn.vnpost.cdp.profile.enums.IdentityType;
import vn.vnpost.cdp.profile.repository.MasterProfileRepository;
import vn.vnpost.cdp.profile.repository.ProfileIdentityLinkRepository;

import java.util.*;

@Slf4j
@Service
public class ProfileMatchingService {

    private static final short PROFILE_MERGED  = 3;
    private static final short PROFILE_DELETED = 5;

    /** Một giá trị khóa khớp nhiều hơn ngưỡng này thì coi như khóa rác, không dùng để tìm candidate. */
    private static final int MAX_PROFILES_PER_KEY = 20;

    private final MasterProfileRepository masterProfileRepository;
    private final ProfileIdentityLinkRepository identityLinkRepository;

    public ProfileMatchingService(MasterProfileRepository masterProfileRepository,
                                  ProfileIdentityLinkRepository identityLinkRepository) {
        this.masterProfileRepository = masterProfileRepository;
        this.identityLinkRepository = identityLinkRepository;
    }

    public List<MasterProfile> findCandidateProfiles(NormalizedProfileData data) {
        Set<Long> candidateIds = new LinkedHashSet<>();
        List<MasterProfile> candidates = new ArrayList<>();

        // 1. Match by identityNo
        if (StringUtils.hasText(data.getIdentityNo())) {
            addProfiles(masterProfileRepository.findByIdentityNo(data.getIdentityNo()),
                    "CCCD", candidateIds, candidates);
        }

        // 2. Match by phone — master_profiles and identity_links
        if (StringUtils.hasText(data.getPhone())) {
            addProfiles(masterProfileRepository.findByPhone(data.getPhone()),
                    "SĐT", candidateIds, candidates);
            addProfilesByLinks(identityLinkRepository
                    .findByIdentityTypeAndIdentityValue("PHONE", data.getPhone()),
                    "PHONE", candidateIds, candidates);
        }

        // 3. Match by email — master_profiles and identity_links
        if (StringUtils.hasText(data.getEmail())) {
            addProfiles(masterProfileRepository.findByEmail(data.getEmail()),
                    "email", candidateIds, candidates);
            addProfilesByLinks(identityLinkRepository
                    .findByIdentityTypeAndIdentityValue("EMAIL", data.getEmail()),
                    "EMAIL", candidateIds, candidates);
        }

        // 4. Match by sourceSystem + sourceCustomerId in identity_links
        if (StringUtils.hasText(data.getSourceSystem()) && StringUtils.hasText(data.getSourceCustomerId())) {
            addProfilesByLinks(identityLinkRepository.findBySourceSystemAndSourceCustomerId(
                    data.getSourceSystem(), data.getSourceCustomerId()),
                    "sourceCustomerId", candidateIds, candidates);
        }

        // 5. Match by taxCode (MST) — khóa doanh nghiệp duy nhất
        if (StringUtils.hasText(data.getTaxCode())) {
            addProfiles(masterProfileRepository.findByTaxCode(data.getTaxCode()),
                    "MST", candidateIds, candidates);
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
            addProfilesByLinks(identityLinkRepository
                    .findByIdentityTypeAndIdentityValue(entry.getKey().name(), entry.getValue()),
                    entry.getKey().name(), candidateIds, candidates);
        }

        log.info("ProfileMatchingService - found {} candidate(s) for sourceSystem={} sourceCustomerId={}",
                candidates.size(), data.getSourceSystem(), data.getSourceCustomerId());
        return candidates;
    }

    /**
     * Thêm các hồ sơ còn dùng được vào pool, giữ nguyên thứ tự phát hiện và không trùng lặp.
     *
     * <p>Một giá trị khóa khớp quá nhiều hồ sơ thì không còn tính phân biệt (SĐT rác / hotline
     * shipper / "0000000000"). Nhận hết sẽ đẩy mọi record vào nhánh CONFLICT với hàng nghìn candidate,
     * nên bỏ qua khóa đó và ghi log để đội dữ liệu xử lý.
     */
    private void addProfiles(List<MasterProfile> found, String keyLabel, Set<Long> candidateIds,
                             List<MasterProfile> candidates) {
        if (found.size() > MAX_PROFILES_PER_KEY) {
            log.warn("ProfileMatchingService - {} hồ sơ cùng {} (> {}) → khóa không có tính phân biệt, bỏ qua",
                    found.size(), keyLabel, MAX_PROFILES_PER_KEY);
            return;
        }
        for (MasterProfile p : found) {
            if (isUsable(p) && candidateIds.add(p.getId())) {
                candidates.add(p);
            }
        }
    }

    /** Như {@link #addProfiles} nhưng đi từ identity_links: chỉ nhận link đang ACTIVE (status=1). */
    private void addProfilesByLinks(List<ProfileIdentityLink> links, String keyLabel,
                                    Set<Long> candidateIds, List<MasterProfile> candidates) {
        List<Long> profileIds = links.stream()
                .filter(l -> l.getStatus() != null && l.getStatus() == 1)
                .map(ProfileIdentityLink::getMasterProfileId)
                .distinct()
                .toList();
        if (profileIds.size() > MAX_PROFILES_PER_KEY) {
            log.warn("ProfileMatchingService - {} hồ sơ cùng link {} (> {}) → khóa không có tính phân biệt, bỏ qua",
                    profileIds.size(), keyLabel, MAX_PROFILES_PER_KEY);
            return;
        }
        for (Long id : profileIds) {
            masterProfileRepository.findById(id)
                    .filter(this::isUsable)
                    .ifPresent(p -> {
                        if (candidateIds.add(p.getId())) candidates.add(p);
                    });
        }
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
