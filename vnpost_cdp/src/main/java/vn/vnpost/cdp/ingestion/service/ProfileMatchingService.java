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
            masterProfileRepository.findByIdentityNo(data.getIdentityNo())
                    .ifPresent(p -> { if (candidateIds.add(p.getId())) candidates.add(p); });
        }

        // 2. Match by phone — master_profiles and identity_links
        if (StringUtils.hasText(data.getPhone())) {
            masterProfileRepository.findByPhone(data.getPhone())
                    .ifPresent(p -> { if (candidateIds.add(p.getId())) candidates.add(p); });
            List<ProfileIdentityLink> phoneLinks = identityLinkRepository
                    .findByIdentityTypeAndIdentityValue("PHONE", data.getPhone());
            phoneLinks.stream()
                    .filter(l -> l.getStatus() != null && l.getStatus() == 1)
                    .forEach(l -> masterProfileRepository.findById(l.getMasterProfileId())
                            .ifPresent(p -> { if (candidateIds.add(p.getId())) candidates.add(p); }));
        }

        // 3. Match by email — master_profiles and identity_links
        if (StringUtils.hasText(data.getEmail())) {
            masterProfileRepository.findByEmail(data.getEmail())
                    .ifPresent(p -> { if (candidateIds.add(p.getId())) candidates.add(p); });
            List<ProfileIdentityLink> emailLinks = identityLinkRepository
                    .findByIdentityTypeAndIdentityValue("EMAIL", data.getEmail());
            emailLinks.stream()
                    .filter(l -> l.getStatus() != null && l.getStatus() == 1)
                    .forEach(l -> masterProfileRepository.findById(l.getMasterProfileId())
                            .ifPresent(p -> { if (candidateIds.add(p.getId())) candidates.add(p); }));
        }

        // 4. Match by sourceSystem + sourceCustomerId in identity_links
        if (StringUtils.hasText(data.getSourceSystem()) && StringUtils.hasText(data.getSourceCustomerId())) {
            identityLinkRepository.findBySourceSystemAndSourceCustomerId(
                    data.getSourceSystem(), data.getSourceCustomerId())
                    .ifPresent(l -> masterProfileRepository.findById(l.getMasterProfileId())
                            .ifPresent(p -> { if (candidateIds.add(p.getId())) candidates.add(p); }));
        }

        // 5. Match by taxCode (MST) — khóa doanh nghiệp duy nhất
        if (StringUtils.hasText(data.getTaxCode())) {
            masterProfileRepository.findByTaxCode(data.getTaxCode())
                    .ifPresent(p -> { if (candidateIds.add(p.getId())) candidates.add(p); });
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
            identityLinkRepository
                    .findByIdentityTypeAndIdentityValue(entry.getKey().name(), entry.getValue())
                    .stream()
                    .filter(l -> l.getStatus() != null && l.getStatus() == 1)
                    .forEach(l -> masterProfileRepository.findById(l.getMasterProfileId())
                            .ifPresent(p -> { if (candidateIds.add(p.getId())) candidates.add(p); }));
        }

        log.info("ProfileMatchingService - found {} candidate(s) for sourceSystem={} sourceCustomerId={}",
                candidates.size(), data.getSourceSystem(), data.getSourceCustomerId());
        return candidates;
    }
}
