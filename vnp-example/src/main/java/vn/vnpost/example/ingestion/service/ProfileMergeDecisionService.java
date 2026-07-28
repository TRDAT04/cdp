package vn.vnpost.example.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.vnpost.example.common.utils.IdentityUtils;
import vn.vnpost.example.ingestion.dto.NormalizedProfileData;
import vn.vnpost.example.ingestion.enums.MergeDecision;
import vn.vnpost.example.ingestion.enums.ProfileSourceSystemCode;
import vn.vnpost.example.profile.dto.match.ProfileMatchReasonCreateItem;
import vn.vnpost.example.profile.dto.match.ProfileMatchScoreResult;
import vn.vnpost.example.profile.entity.MasterProfile;
import vn.vnpost.example.profile.enums.IdentityType;
import vn.vnpost.example.profile.repository.ProfileIdentityLinkRepository;
import vn.vnpost.example.profile.service.match.ProfileMatchScoreService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Decides the merge strategy for an incoming profile record.
 *
 * Source trust tiers:
 *   HIGH   — CRM, CORE
 *   MEDIUM — MYVNPOST, PORTAL
 *   LOW    — CMS, WEBSITE
 *
 * Decision flow (single candidate):
 *  1. alreadyLinked (same sourceSystem+sourceCustomerId, status=1) → AUTO_MERGE
 *  2. Deterministic checks on identity fields:
 *     - identityNo conflict                → NEED_REVIEW
 *     - identityNo exact match             → AUTO_MERGE
 *  3. Ambiguous (no Identity No conflict/match) — delegate to Score:
 *     - identityConflict detected by scorer → NEED_REVIEW
 *     - Score >= 95 && !identityConflict    → AUTO_MERGE
 *     - Score >= 70                         → CREATE_MATCH_CANDIDATE
 *     - Score < 70                          → CREATE_NEW_PROFILE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileMergeDecisionService {

    private final ProfileIdentityLinkRepository identityLinkRepository;
    private final ProfileMatchScoreService scoreService;

    public Mono<MergeDecision> decide(NormalizedProfileData data, List<MasterProfile> candidates) {
        String sourceSystem = data.getSourceSystem();

        if (!ProfileSourceSystemCode.isValid(sourceSystem)) {
            log.warn("ProfileMergeDecisionService - unknown sourceSystem={}, REJECT", sourceSystem);
            return Mono.just(MergeDecision.REJECT);
        }

        boolean hasIdentity = StringUtils.hasText(data.getIdentityNo())
                || StringUtils.hasText(data.getPhone())
                || StringUtils.hasText(data.getEmail())
                || StringUtils.hasText(data.getSourceCustomerId());

        if (!hasIdentity) {
            log.warn("ProfileMergeDecisionService - no useful identity in payload, REJECT");
            return Mono.just(MergeDecision.REJECT);
        }

        if (candidates.isEmpty()) {
            log.info("ProfileMergeDecisionService - no candidates, CREATE_NEW_PROFILE");
            return Mono.just(MergeDecision.CREATE_NEW_PROFILE);
        }

        if (candidates.size() > 1) {
            log.info("ProfileMergeDecisionService - {} candidates, CONFLICT", candidates.size());
            return Mono.just(MergeDecision.CONFLICT);
        }

        MasterProfile candidate = candidates.get(0);

        // ── Fast-path 1: Already linked (same source+customer → known identity) ──
        return identityLinkRepository
                .findBySourceSystemAndSourceCustomerIdAndStatus(sourceSystem, data.getSourceCustomerId(), (short) 1)
                .map(link -> link.getMasterProfileId().equals(candidate.getId()))
                .defaultIfEmpty(false)
                .flatMap(alreadyLinked -> {
                    if (alreadyLinked) {
                        log.info("ProfileMergeDecisionService - already linked to candidate profile, AUTO_MERGE");
                        return Mono.just(MergeDecision.AUTO_MERGE);
                    }
                    return decideAfterLinkCheck(data, candidate);
                });
    }

    private Mono<MergeDecision> decideAfterLinkCheck(NormalizedProfileData data, MasterProfile candidate) {
        // ── Normalize identity fields for deterministic comparison ──
        String incomingIdNo   = IdentityUtils.normalizeText(data.getIdentityNo());
        String candidateIdNo  = IdentityUtils.normalizeText(candidate.getIdentityNo());

        boolean identityNoMatch    = hasText(incomingIdNo)  && hasText(candidateIdNo)  && incomingIdNo.equals(candidateIdNo);
        boolean identityNoConflict = hasText(incomingIdNo)  && hasText(candidateIdNo)  && !incomingIdNo.equals(candidateIdNo);

        // ── Fast-path 2: Deterministic identity checks ──
        if (identityNoConflict) {
            log.info("ProfileMergeDecisionService - identityNo conflict → NEED_REVIEW");
            return Mono.just(MergeDecision.NEED_REVIEW);
        }
        if (identityNoMatch) {
            log.info("ProfileMergeDecisionService - identityNo exact match → AUTO_MERGE");
            return Mono.just(MergeDecision.AUTO_MERGE);
        }

        // ── Fast-path 2b: MST (taxCode) deterministic — mirror CCCD ──
        String incomingTax  = IdentityUtils.normalizeText(data.getTaxCode());
        String candidateTax = IdentityUtils.normalizeText(candidate.getTaxCode());
        if (hasText(incomingTax) && hasText(candidateTax)) {
            if (!incomingTax.equals(candidateTax)) {
                log.info("ProfileMergeDecisionService - taxCode conflict → NEED_REVIEW");
                return Mono.just(MergeDecision.NEED_REVIEW);
            }
            String incomingName  = IdentityUtils.normalizeName(data.getFullName());
            String candidateName = IdentityUtils.normalizeName(candidate.getFullName());
            boolean nameOk = !hasText(incomingName) || !hasText(candidateName)
                    || IdentityUtils.calculateNameSimilarity(incomingName, candidateName) >= 75;
            if (nameOk) {
                log.info("ProfileMergeDecisionService - taxCode match (name ok) → AUTO_MERGE");
                return Mono.just(MergeDecision.AUTO_MERGE);
            }
            log.info("ProfileMergeDecisionService - taxCode match but name too different → NEED_REVIEW");
            return Mono.just(MergeDecision.NEED_REVIEW);
        }

        // ── Fast-path 2c: Khớp khóa duy nhất do nguồn cấp (KHL/CRM/PostID/AppUserId/Payment) ──
        return matchedByUniqueTypedId(data, candidate)
                .flatMap(matched -> {
                    if (matched) {
                        log.info("ProfileMergeDecisionService - unique typed identifier match → AUTO_MERGE");
                        return Mono.just(MergeDecision.AUTO_MERGE);
                    }
                    return decideByEnrichmentOrScore(data, candidate);
                });
    }

    private Mono<MergeDecision> decideByEnrichmentOrScore(NormalizedProfileData data, MasterProfile candidate) {
        // ── Fast-path 3: Event enrichment (PROFILE_ENRICHED) ──
        if (isEnrichmentEvent(data)) {
            String incomingPhone  = IdentityUtils.normalizePhone(data.getPhone());
            String candidatePhone = IdentityUtils.normalizePhone(candidate.getPhone());
            String incomingEmail  = IdentityUtils.normalizeEmail(data.getEmail());
            String candidateEmail = IdentityUtils.normalizeEmail(candidate.getEmail());

            boolean phoneMatch = hasText(incomingPhone) && hasText(candidatePhone)
                    && incomingPhone.equals(candidatePhone);
            boolean emailMatch = hasText(incomingEmail) && hasText(candidateEmail)
                    && incomingEmail.equals(candidateEmail);

            if (phoneMatch || emailMatch) {
                log.info("ProfileMergeDecisionService - enrichment deterministic match (phone={}, email={}) → AUTO_MERGE",
                        phoneMatch, emailMatch);
                return Mono.just(MergeDecision.AUTO_MERGE);
            }
        }

        // ── Score-based path: ambiguous case (no direct match OR conflict found) ──
        ProfileMatchScoreResult scoreResult = scoreService.calculate(data, candidate);
        BigDecimal score = scoreResult.getScore();

        log.info("ProfileMergeDecisionService - ambiguous → Score profileId={}, score={}, level={}, conflict={}, reasons={}",
                candidate.getId(),
                score,
                scoreResult.getMatchLevel(),
                scoreResult.isIdentityConflict(),
                scoreResult.getReasons().stream()
                        .map(ProfileMatchReasonCreateItem::getReasonType)
                        .toList()
        );

        if (scoreResult.isIdentityConflict()) {
            log.info("ProfileMergeDecisionService - scorer detected identity conflict → NEED_REVIEW: score={}", score);
            return Mono.just(MergeDecision.NEED_REVIEW);
        }

        if (scoreResult.isAutoMergeRecommended()) {
            log.info("ProfileMergeDecisionService - score >= 95 and no conflict → AUTO_MERGE: score={}", score);
            return Mono.just(MergeDecision.AUTO_MERGE);
        }

        if (score.compareTo(BigDecimal.valueOf(70)) >= 0) {
            log.info("ProfileMergeDecisionService - score >= 70 → CREATE_MATCH_CANDIDATE: score={}", score);
            return Mono.just(MergeDecision.CREATE_MATCH_CANDIDATE);
        }

        log.info("ProfileMergeDecisionService - score < 70, no identity overlap → CREATE_NEW_PROFILE: score={}", score);
        return Mono.just(MergeDecision.CREATE_NEW_PROFILE);
    }

    private boolean hasText(String s) {
        return StringUtils.hasText(s);
    }

    /** Event làm giàu định danh (enrichment), ví dụ PROFILE_ENRICHED từ MyVNPost/Website/PayPost. */
    private boolean isEnrichmentEvent(NormalizedProfileData data) {
        return "PROFILE_ENRICHED".equalsIgnoreCase(data.getEventType());
    }

    /**
     * Kiểm tra candidate có link ACTIVE khớp CHÍNH XÁC một khóa duy nhất do nguồn cấp
     * (KHL_CODE / CRM_ID / POST_ID / APP_USER_ID / PAYMENT_ID) với giá trị trong data.
     * Không dùng DEVICE_ID/COOKIE_ID (để dành Probabilistic Matching, tránh false-positive).
     */
    private Mono<Boolean> matchedByUniqueTypedId(NormalizedProfileData data, MasterProfile candidate) {
        return Flux.concat(
                hasTypedLink(candidate.getId(), IdentityType.KHL_CODE,     data.getKhlCode()),
                hasTypedLink(candidate.getId(), IdentityType.CRM_ID,       data.getCrmId()),
                hasTypedLink(candidate.getId(), IdentityType.POST_ID,      data.getPostId()),
                hasTypedLink(candidate.getId(), IdentityType.APP_USER_ID,  data.getAppUserId()),
                hasTypedLink(candidate.getId(), IdentityType.PAYMENT_ID,   data.getPaymentId())
        ).any(Boolean::booleanValue);
    }

    private Mono<Boolean> hasTypedLink(Long masterProfileId, IdentityType type, String value) {
        if (!hasText(value)) {
            return Mono.just(false);
        }
        return identityLinkRepository
                .findByMasterProfileIdAndIdentityTypeAndIdentityValue(masterProfileId, type.name(), value)
                .any(l -> l.getStatus() != null && l.getStatus() == 1);
    }
}
