package vn.vnpost.cdp.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.ingestion.enums.MergeDecision;
import vn.vnpost.cdp.ingestion.enums.ProfileSourceSystemCode;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchReasonCreateItem;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.repository.ProfileIdentityLinkRepository;
import vn.vnpost.cdp.profile.service.match.ProfileMatchScoreService;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchScoreResult;
import vn.vnpost.cdp.common.utils.IdentityUtils;

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

        // ── Fast-path 1: Already linked (same source+customer → known identity) ──
        boolean alreadyLinked = identityLinkRepository
                .findBySourceSystemAndSourceCustomerIdAndStatus(sourceSystem, data.getSourceCustomerId(), (short) 1)
                .map(link -> link.getMasterProfileId().equals(candidate.getId()))
                .orElse(false);

        if (alreadyLinked) {
            log.info("ProfileMergeDecisionService - already linked to candidate profile, AUTO_MERGE");
            return MergeDecision.AUTO_MERGE;
        }

        // ── Normalize identity fields for deterministic comparison ──
        String incomingIdNo   = IdentityUtils.normalizeText(data.getIdentityNo());
        String candidateIdNo  = IdentityUtils.normalizeText(candidate.getIdentityNo());
        String incomingPhone  = IdentityUtils.normalizePhone(data.getPhone());
        String candidatePhone = IdentityUtils.normalizePhone(candidate.getPhone());
        String incomingEmail  = IdentityUtils.normalizeEmail(data.getEmail());
        String candidateEmail = IdentityUtils.normalizeEmail(candidate.getEmail());

        boolean identityNoMatch    = hasText(incomingIdNo)  && hasText(candidateIdNo)  && incomingIdNo.equals(candidateIdNo);
        boolean identityNoConflict = hasText(incomingIdNo)  && hasText(candidateIdNo)  && !incomingIdNo.equals(candidateIdNo);

        // ── Fast-path 2: Deterministic identity checks ──
        if (identityNoConflict) {
            log.info("ProfileMergeDecisionService - identityNo conflict → NEED_REVIEW");
            return MergeDecision.NEED_REVIEW;
        }
        if (identityNoMatch) {
            log.info("ProfileMergeDecisionService - identityNo exact match → AUTO_MERGE");
            return MergeDecision.AUTO_MERGE;
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
            return MergeDecision.NEED_REVIEW;
        }

        if (scoreResult.isAutoMergeRecommended()) {
            log.info("ProfileMergeDecisionService - score >= 95 and no conflict → AUTO_MERGE: score={}", score);
            return MergeDecision.AUTO_MERGE;
        }

        if (score.compareTo(BigDecimal.valueOf(70)) >= 0) {
            log.info("ProfileMergeDecisionService - score >= 70 → CREATE_MATCH_CANDIDATE: score={}", score);
            return MergeDecision.CREATE_MATCH_CANDIDATE;
        }

        log.info("ProfileMergeDecisionService - score < 70, no identity overlap → CREATE_NEW_PROFILE: score={}", score);
        return MergeDecision.CREATE_NEW_PROFILE;
    }

    private boolean hasText(String s) {
        return StringUtils.hasText(s);
    }
}
