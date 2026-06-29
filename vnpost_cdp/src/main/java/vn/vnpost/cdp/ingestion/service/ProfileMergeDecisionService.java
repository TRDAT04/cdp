package vn.vnpost.cdp.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.ingestion.enums.MergeDecision;
import vn.vnpost.cdp.ingestion.enums.ProfileSourceSystemCode;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.repository.ProfileIdentityLinkRepository;

import java.util.List;

/**
 * Decides the merge strategy for an incoming profile record.
 *
 * Source trust tiers:
 *   HIGH   — CRM, CORE
 *   MEDIUM — MYVNPOST, PORTAL
 *   LOW    — CMS, WEBSITE
 *
 * Key rule: phone / email match alone is NOT sufficient for AUTO_MERGE unless the
 * source is CRM and there are no conflicting identity fields.  Medium/low-trust
 * sources (CMS, PORTAL, MYVNPOST) must go through NEED_REVIEW or
 * CREATE_MATCH_CANDIDATE even when a phone or email matches.
 */
@Slf4j
@Service
public class ProfileMergeDecisionService {

    private final ProfileIdentityLinkRepository identityLinkRepository;

    public ProfileMergeDecisionService(ProfileIdentityLinkRepository identityLinkRepository) {
        this.identityLinkRepository = identityLinkRepository;
    }

    public MergeDecision decide(NormalizedProfileData data, List<MasterProfile> candidates) {
        String sourceSystem = data.getSourceSystem();

        if (!ProfileSourceSystemCode.isValid(sourceSystem)) {
            log.warn("ProfileMergeDecisionService - unknown sourceSystem={}, REJECT", sourceSystem);
            return MergeDecision.REJECT;
        }

        boolean hasIdentity = StringUtils.hasText(data.getIdentityNo())
                || StringUtils.hasText(data.getPhone())
                || StringUtils.hasText(data.getEmail())
                || StringUtils.hasText(data.getSourceCustomerId());

        if (!hasIdentity) {
            log.warn("ProfileMergeDecisionService - no useful identity in payload, REJECT");
            return MergeDecision.REJECT;
        }

        if (candidates.isEmpty()) {
            log.info("ProfileMergeDecisionService - no candidates, CREATE_NEW_PROFILE");
            return MergeDecision.CREATE_NEW_PROFILE;
        }

        if (candidates.size() > 1) {
            log.info("ProfileMergeDecisionService - {} candidates, CONFLICT", candidates.size());
            return MergeDecision.CONFLICT;
        }

        MasterProfile candidate = candidates.get(0);

        // Only ACTIVE links (status=1) qualify for auto-merge
        boolean alreadyLinked = identityLinkRepository
                .findBySourceSystemAndSourceCustomerIdAndStatus(sourceSystem, data.getSourceCustomerId(), (short) 1)
                .map(link -> link.getMasterProfileId().equals(candidate.getId()))
                .orElse(false);

        if (alreadyLinked) {
            log.info("ProfileMergeDecisionService - already linked to candidate profile, AUTO_MERGE");
            return MergeDecision.AUTO_MERGE;
        }

        String incomingIdentityNo = normalizeText(data.getIdentityNo());
        String candidateIdentityNo = normalizeText(candidate.getIdentityNo());

        String incomingPhone = normalizePhone(data.getPhone());
        String candidatePhone = normalizePhone(candidate.getPhone());

        String incomingEmail = normalizeEmail(data.getEmail());
        String candidateEmail = normalizeEmail(candidate.getEmail());

        boolean identityNoMatch = StringUtils.hasText(incomingIdentityNo)
                && StringUtils.hasText(candidateIdentityNo)
                && incomingIdentityNo.equals(candidateIdentityNo);

        boolean phoneMatch = StringUtils.hasText(incomingPhone)
                && StringUtils.hasText(candidatePhone)
                && incomingPhone.equals(candidatePhone);

        boolean emailMatch = StringUtils.hasText(incomingEmail)
                && StringUtils.hasText(candidateEmail)
                && incomingEmail.equals(candidateEmail);

        boolean identityNoConflict = StringUtils.hasText(incomingIdentityNo)
                && StringUtils.hasText(candidateIdentityNo)
                && !incomingIdentityNo.equals(candidateIdentityNo);

        boolean phoneConflict = StringUtils.hasText(incomingPhone)
                && StringUtils.hasText(candidatePhone)
                && !incomingPhone.equals(candidatePhone);

        boolean emailConflict = StringUtils.hasText(incomingEmail)
                && StringUtils.hasText(candidateEmail)
                && !incomingEmail.equals(candidateEmail);

        boolean isCrm = "CRM".equalsIgnoreCase(sourceSystem);
        boolean isCms = "CMS".equalsIgnoreCase(sourceSystem);
        boolean isPortal = "PORTAL".equalsIgnoreCase(sourceSystem);
        boolean isMyVnpost = "MYVNPOST".equalsIgnoreCase(sourceSystem);

        if (identityNoConflict) {
            log.info("ProfileMergeDecisionService - identityNo conflict, NEED_REVIEW");
            return MergeDecision.NEED_REVIEW;
        }

        if (isCrm) {
            if (identityNoMatch) {
                log.info("ProfileMergeDecisionService - CRM identityNo match, AUTO_MERGE");
                return MergeDecision.AUTO_MERGE;
            }

            if (phoneMatch && !emailConflict) {
                log.info("ProfileMergeDecisionService - CRM phone match and no email conflict, AUTO_MERGE");
                return MergeDecision.AUTO_MERGE;
            }

            if (emailMatch && !phoneConflict) {
                log.info("ProfileMergeDecisionService - CRM email match and no phone conflict, AUTO_MERGE");
                return MergeDecision.AUTO_MERGE;
            }

            log.info("ProfileMergeDecisionService - CRM ambiguous/conflict, NEED_REVIEW");
            return MergeDecision.NEED_REVIEW;
        }

        if (isCms) {
            /*
             * Case 3:
             * CMS same email but different phone.
             * This is field-level conflict, not auto merge.
             */
            if ((emailMatch && phoneConflict) || (phoneMatch && emailConflict)) {
                log.info("ProfileMergeDecisionService - CMS matched but identity field conflict, NEED_REVIEW");
                return MergeDecision.NEED_REVIEW;
            }

            if (identityNoMatch || phoneMatch || emailMatch) {
                log.info("ProfileMergeDecisionService - CMS matched existing profile but not linked, NEED_REVIEW");
                return MergeDecision.NEED_REVIEW;
            }

            log.info("ProfileMergeDecisionService - CMS ambiguous, NEED_REVIEW");
            return MergeDecision.NEED_REVIEW;
        }

        if (isPortal || isMyVnpost) {
            /*
             * Case 4:
             * PORTAL same phone but different email.
             * This should become match candidate, not field conflict.
             */
            if (phoneMatch || emailMatch || identityNoMatch) {
                log.info("ProfileMergeDecisionService - {} soft matched existing profile, CREATE_MATCH_CANDIDATE", sourceSystem);
                return MergeDecision.CREATE_MATCH_CANDIDATE;
            }

            /*
             * If no direct phone/email/identity match, but matching service still returned one candidate,
             * it is probably a fuzzy match by name/province.
             * This should also become match candidate.
             */
            log.info("ProfileMergeDecisionService - {} fuzzy/ambiguous candidate, CREATE_MATCH_CANDIDATE", sourceSystem);
            return MergeDecision.CREATE_MATCH_CANDIDATE;
        }

        log.info("ProfileMergeDecisionService - unhandled valid sourceSystem={}, NEED_REVIEW", sourceSystem);
        return MergeDecision.NEED_REVIEW;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }

        String digits = phone.replaceAll("[^0-9]", "");

        if (digits.startsWith("84") && digits.length() > 9) {
            digits = "0" + digits.substring(2);
        }

        return digits;
    }
}
