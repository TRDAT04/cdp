package vn.vnpost.example.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.vnpost.example.ingestion.dto.NormalizedProfileData;
import vn.vnpost.example.profile.entity.MasterProfile;
import vn.vnpost.example.profile.enums.IdentityType;
import vn.vnpost.example.profile.repository.MasterProfileRepository;
import vn.vnpost.example.profile.repository.ProfileIdentityLinkRepository;

import java.util.*;

@Slf4j
@Service
public class ProfileMatchingService {

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
            steps.add(masterProfileRepository.findByIdentityNo(data.getIdentityNo()).flux());
        }

        // 2. Match by phone — master_profiles and identity_links
        if (StringUtils.hasText(data.getPhone())) {
            steps.add(masterProfileRepository.findByPhone(data.getPhone()).flux());
            steps.add(identityLinkRepository.findByIdentityTypeAndIdentityValue("PHONE", data.getPhone())
                    .filter(l -> l.getStatus() != null && l.getStatus() == 1)
                    .concatMap(l -> masterProfileRepository.findById(l.getMasterProfileId())));
        }

        // 3. Match by email — master_profiles and identity_links
        if (StringUtils.hasText(data.getEmail())) {
            steps.add(masterProfileRepository.findByEmail(data.getEmail()).flux());
            steps.add(identityLinkRepository.findByIdentityTypeAndIdentityValue("EMAIL", data.getEmail())
                    .filter(l -> l.getStatus() != null && l.getStatus() == 1)
                    .concatMap(l -> masterProfileRepository.findById(l.getMasterProfileId())));
        }

        // 4. Match by sourceSystem + sourceCustomerId in identity_links
        if (StringUtils.hasText(data.getSourceSystem()) && StringUtils.hasText(data.getSourceCustomerId())) {
            steps.add(identityLinkRepository
                    .findBySourceSystemAndSourceCustomerId(data.getSourceSystem(), data.getSourceCustomerId())
                    .flux()
                    .concatMap(l -> masterProfileRepository.findById(l.getMasterProfileId())));
        }

        // 5. Match by taxCode (MST) — khóa doanh nghiệp duy nhất
        if (StringUtils.hasText(data.getTaxCode())) {
            steps.add(masterProfileRepository.findByTaxCode(data.getTaxCode()).flux());
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
            steps.add(identityLinkRepository
                    .findByIdentityTypeAndIdentityValue(entry.getKey().name(), entry.getValue())
                    .filter(l -> l.getStatus() != null && l.getStatus() == 1)
                    .concatMap(l -> masterProfileRepository.findById(l.getMasterProfileId())));
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
}
