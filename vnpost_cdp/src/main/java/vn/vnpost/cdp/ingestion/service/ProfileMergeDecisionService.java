package vn.vnpost.cdp.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.common.utils.IdentityUtils;
import vn.vnpost.cdp.ingestion.dto.MergeDecisionResult;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.ingestion.enums.MergeDecision;
import vn.vnpost.cdp.ingestion.enums.ProfileSourceSystemCode;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchReasonCreateItem;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchScoreResult;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.entity.ProfileIdentityLink;
import vn.vnpost.cdp.profile.enums.IdentityType;
import vn.vnpost.cdp.profile.repository.ProfileIdentityLinkRepository;
import vn.vnpost.cdp.profile.service.match.IdentityMatchThresholds;
import vn.vnpost.cdp.profile.service.match.ProfileMatchScoreService;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Decides the merge strategy for an incoming profile record.
 *
 * Source trust tiers:
 *   HIGH   — CRM, CORE
 *   MEDIUM — MYVNPOST, PORTAL
 *   LOW    — CMS, WEBSITE
 *
 * Nhiều candidate: thử tách bằng khóa mạnh (link nguồn → CCCD → MST → khóa typed). Nếu CHỈ MỘT
 * candidate khớp một trong các khóa đó thì nó là đích và chuỗi rule bên dưới chạy tiếp bình thường;
 * chỉ khi không tách được (0 hoặc ≥2 candidate cùng khớp) mới là CONFLICT. Trước đây size&gt;1 trả
 * CONFLICT ngay, tức là nguyên tắc "khóa mạnh xét trước" bị bỏ qua hoàn toàn khi nhiều hồ sơ dùng
 * chung SĐT — dù trong đó có một hồ sơ khớp CCCD tuyệt đối.
 *
 * Decision flow (sau khi đã chốt được 1 candidate):
 *  1. alreadyLinked (same sourceSystem+sourceCustomerId, status=1) → AUTO_MERGE
 *  2. Deterministic checks on identity fields:
 *     - identityNo conflict                → NEED_REVIEW
 *     - identityNo exact match             → AUTO_MERGE
 *  3. Ambiguous (no Identity No conflict/match) — delegate to Score:
 *     - identityConflict detected by scorer → NEED_REVIEW
 *     - Score >= 95 && !identityConflict    → AUTO_MERGE
 *     - Score >= 70                         → CREATE_MATCH_CANDIDATE
 *     - Score < 70                          → CREATE_NEW_PROFILE
 *
 * Lưu ý về nhánh score &lt; 70: hồ sơ vẫn được tạo qua createNewProfile (có Unomi sync + change log),
 * và match yếu (VD chỉ trùng SĐT 40đ) VẪN được gắn cờ để đối soát — nhưng bằng
 * ProfileMatchCandidateServiceImpl.detectAndCreateCandidatesForProfile() chạy sau khi đã lưu hồ sơ,
 * nơi sàn tạo candidate là 35. Cố tình KHÔNG hạ ngưỡng này xuống 35: nhánh CREATE_MATCH_CANDIDATE
 * dùng createProfileForReview(), hàm đó KHÔNG chạy post-processing nên không sync Unomi và không ghi
 * change log — hạ ngưỡng sẽ làm mọi match yếu biến mất khỏi segment/campaign.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileMergeDecisionService {

    /** Từ ngưỡng này trở lên thì tạo match candidate ngay trong luồng ingest (đường review). */
    private static final BigDecimal MATCH_CANDIDATE_SCORE =
            BigDecimal.valueOf(IdentityMatchThresholds.MATCH_CANDIDATE_SCORE);

    private final ProfileIdentityLinkRepository identityLinkRepository;
    private final ProfileMatchScoreService scoreService;

    public Mono<MergeDecisionResult> decide(NormalizedProfileData data, List<MasterProfile> candidates) {
        String sourceSystem = data.getSourceSystem();

        if (!ProfileSourceSystemCode.isValid(sourceSystem)) {
            log.warn("ProfileMergeDecisionService - unknown sourceSystem={}, REJECT", sourceSystem);
            return Mono.just(MergeDecisionResult.of(MergeDecision.REJECT));
        }

        if (!hasUsableIdentity(data)) {
            log.warn("ProfileMergeDecisionService - no useful identity in payload, REJECT");
            return Mono.just(MergeDecisionResult.of(MergeDecision.REJECT));
        }

        if (candidates.isEmpty()) {
            log.info("ProfileMergeDecisionService - no candidates, CREATE_NEW_PROFILE");
            return Mono.just(MergeDecisionResult.of(MergeDecision.CREATE_NEW_PROFILE));
        }

        // Nhiều candidate KHÔNG mặc nhiên là xung đột: thường chỉ là vài hồ sơ dùng chung SĐT. Thử
        // tách bằng khóa mạnh trước, để nguyên tắc "khóa mạnh xét trước" vẫn đúng khi có >1 candidate.
        return resolveSingleCandidate(data, sourceSystem, candidates)
                .flatMap(candidate -> decideForCandidate(data, sourceSystem, candidate))
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.info("ProfileMergeDecisionService - {} candidates, không tách được bằng khóa mạnh "
                            + "→ CONFLICT", candidates.size());
                    return MergeDecisionResult.of(MergeDecision.CONFLICT);
                }));
    }

    private Mono<MergeDecisionResult> decideForCandidate(NormalizedProfileData data, String sourceSystem,
                                                        MasterProfile candidate) {
        // ── Fast-path 1: Already linked (same source+customer → known identity) ──
        return identityLinkRepository
                .findBySourceSystemAndSourceCustomerIdAndStatus(sourceSystem, data.getSourceCustomerId(), (short) 1)
                .any(link -> link.getMasterProfileId().equals(candidate.getId()))
                .flatMap(alreadyLinked -> {
                    if (Boolean.TRUE.equals(alreadyLinked)) {
                        log.info("ProfileMergeDecisionService - already linked to candidate profile, AUTO_MERGE");
                        return Mono.just(MergeDecisionResult.of(MergeDecision.AUTO_MERGE, candidate));
                    }
                    return decideAfterLinkCheck(data, candidate);
                });
    }

    private Mono<MergeDecisionResult> decideAfterLinkCheck(NormalizedProfileData data, MasterProfile candidate) {
        // ── Normalize identity fields for deterministic comparison ──
        String incomingIdNo   = IdentityUtils.normalizeIdentityNo(data.getIdentityNo());
        String candidateIdNo  = IdentityUtils.normalizeIdentityNo(candidate.getIdentityNo());

        boolean identityNoMatch    = hasText(incomingIdNo) && hasText(candidateIdNo) && incomingIdNo.equals(candidateIdNo);
        boolean identityNoConflict = hasText(incomingIdNo) && hasText(candidateIdNo) && !incomingIdNo.equals(candidateIdNo);

        // ── Fast-path 2: Deterministic identity checks ──
        if (identityNoConflict) {
            log.info("ProfileMergeDecisionService - identityNo conflict → NEED_REVIEW");
            return Mono.just(MergeDecisionResult.of(MergeDecision.NEED_REVIEW, candidate));
        }
        if (identityNoMatch) {
            log.info("ProfileMergeDecisionService - identityNo exact match → AUTO_MERGE");
            return Mono.just(MergeDecisionResult.of(MergeDecision.AUTO_MERGE, candidate));
        }

        // ── Fast-path 2b: MST (taxCode) deterministic — mirror CCCD ──
        // MST là khóa pháp lý duy nhất của doanh nghiệp. Khác MST → NEED_REVIEW.
        // Khớp MST + tên không lệch quá xa (>=75%, nhất quán ngưỡng fullName) → AUTO_MERGE;
        // khớp MST nhưng tên lệch xa → NEED_REVIEW để phòng dùng nhầm/chia sẻ MST.
        String incomingTax  = IdentityUtils.normalizeIdentityNo(data.getTaxCode());
        String candidateTax = IdentityUtils.normalizeIdentityNo(candidate.getTaxCode());
        if (hasText(incomingTax) && hasText(candidateTax)) {
            if (!incomingTax.equals(candidateTax)) {
                log.info("ProfileMergeDecisionService - taxCode conflict → NEED_REVIEW");
                return Mono.just(MergeDecisionResult.of(MergeDecision.NEED_REVIEW, candidate));
            }
            String incomingName  = IdentityUtils.normalizeName(data.getFullName());
            String candidateName = IdentityUtils.normalizeName(candidate.getFullName());
            boolean nameOk = !hasText(incomingName) || !hasText(candidateName)
                    || IdentityUtils.calculateNameSimilarity(incomingName, candidateName)
                            >= IdentityMatchThresholds.NAME_SIMILARITY_MIN;
            if (nameOk) {
                log.info("ProfileMergeDecisionService - taxCode match (name ok) → AUTO_MERGE");
                return Mono.just(MergeDecisionResult.of(MergeDecision.AUTO_MERGE, candidate));
            }
            log.info("ProfileMergeDecisionService - taxCode match but name too different → NEED_REVIEW");
            return Mono.just(MergeDecisionResult.of(MergeDecision.NEED_REVIEW, candidate));
        }

        // ── Fast-path 2c: Khớp khóa duy nhất do nguồn cấp (KHL/CRM/PostID/AppUserId/Payment) ──
        // Deterministic, nhất quán với FP1 (link sourceSystem+sourceCustomerId). KHÔNG gồm DEVICE_ID/COOKIE_ID.
        return matchedByUniqueTypedId(data, candidate)
                .flatMap(matched -> {
                    if (Boolean.TRUE.equals(matched)) {
                        log.info("ProfileMergeDecisionService - unique typed identifier match → AUTO_MERGE");
                        return Mono.just(MergeDecisionResult.of(MergeDecision.AUTO_MERGE, candidate));
                    }
                    return decideByEnrichmentOrScore(data, candidate);
                });
    }

    private Mono<MergeDecisionResult> decideByEnrichmentOrScore(NormalizedProfileData data, MasterProfile candidate) {
        // ── Fast-path 3: Event enrichment (PROFILE_ENRICHED) ──
        // Event enrichment đến từ nguồn đã xác thực người dùng (app đã login, session gắn email,
        // giao dịch gắn phone...). Với 1 candidate duy nhất và khớp deterministic phone/email
        // (không có xung đột identityNo) → AUTO_MERGE để KHÔNG tạo profile trùng.
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
                return Mono.just(MergeDecisionResult.of(MergeDecision.AUTO_MERGE, candidate));
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
            return Mono.just(MergeDecisionResult.of(MergeDecision.NEED_REVIEW, candidate));
        }

        if (scoreResult.isAutoMergeRecommended()) {
            log.info("ProfileMergeDecisionService - score >= {} and no conflict → AUTO_MERGE: score={}",
                    IdentityMatchThresholds.AUTO_MERGE_SCORE, score);
            return Mono.just(MergeDecisionResult.of(MergeDecision.AUTO_MERGE, candidate));
        }

        if (score.compareTo(MATCH_CANDIDATE_SCORE) >= 0) {
            log.info("ProfileMergeDecisionService - score >= {} → CREATE_MATCH_CANDIDATE: score={}",
                    MATCH_CANDIDATE_SCORE, score);
            return Mono.just(MergeDecisionResult.of(MergeDecision.CREATE_MATCH_CANDIDATE, candidate));
        }

        // Match yếu vẫn được gắn cờ, nhưng qua detectAndCreateCandidatesForProfile() (sàn 35)
        // để hồ sơ đi đường createNewProfile đầy đủ (Unomi sync + change log). Xem Javadoc của class.
        log.info("ProfileMergeDecisionService - score < {}, no identity overlap → CREATE_NEW_PROFILE: score={}",
                MATCH_CANDIDATE_SCORE, score);
        return Mono.just(MergeDecisionResult.of(MergeDecision.CREATE_NEW_PROFILE));
    }

    private boolean hasText(String s) {
        return StringUtils.hasText(s);
    }

    /**
     * Payload có ít nhất một khóa dùng được để đối sánh. Gồm cả taxCode và các khóa typed
     * (PostID/KHL/CRM/AppUser/Payment) vì {@code ProfileMatchingService} thực sự tra candidate bằng
     * chúng — thiếu chúng ở đây thì một record chỉ có MST hoặc chỉ có PostID sẽ bị REJECT dù hệ thống
     * hoàn toàn đủ khả năng khớp.
     */
    private boolean hasUsableIdentity(NormalizedProfileData data) {
        return hasText(data.getIdentityNo())
                || hasText(data.getPhone())
                || hasText(data.getEmail())
                || hasText(data.getSourceCustomerId())
                || hasText(data.getTaxCode())
                || hasText(data.getPostId())
                || hasText(data.getKhlCode())
                || hasText(data.getCrmId())
                || hasText(data.getAppUserId())
                || hasText(data.getPaymentId());
    }

    /**
     * Chốt ra candidate duy nhất để chạy chuỗi rule. Có đúng 1 candidate thì trả luôn. Có nhiều
     * candidate thì thử tách bằng khóa mạnh theo thứ tự ưu tiên (link nguồn → CCCD → MST → khóa
     * typed): nếu CHỈ MỘT candidate khớp khóa mạnh đang xét thì nó là đích — các candidate còn lại
     * chỉ trùng tín hiệu yếu (thường là dùng chung SĐT) nên đây không phải xung đột thật.
     *
     * <p>Trả {@code Mono.empty()} = xung đột thật (không khóa mạnh nào tách được, hoặc ≥2 candidate
     * cùng khớp một khóa mạnh) → caller trả CONFLICT.
     *
     * <p>Các candidate KHÔNG được chọn vẫn không bị bỏ rơi: sau khi merge,
     * {@code detectAndCreateCandidatesForProfile()} sẽ phát hiện chúng qua tín hiệu yếu và tạo match
     * candidate để đối soát.
     */
    private Mono<MasterProfile> resolveSingleCandidate(NormalizedProfileData data, String sourceSystem,
                                                      List<MasterProfile> candidates) {
        if (candidates.size() == 1) {
            return Mono.just(candidates.get(0));
        }

        // 1. Link nguồn: chính hệ thống nguồn khẳng định "đây là khách hàng X".
        Mono<MasterProfile> bySourceLink = identityLinkRepository
                .findBySourceSystemAndSourceCustomerIdAndStatus(sourceSystem, data.getSourceCustomerId(), (short) 1)
                .map(ProfileIdentityLink::getMasterProfileId)
                .collect(Collectors.toSet())
                .flatMap(linkedIds -> pickUnique(candidates,
                        c -> linkedIds.contains(c.getId()), "sourceCustomerId link"));

        // 2. CCCD
        String incomingIdNo = IdentityUtils.normalizeIdentityNo(data.getIdentityNo());
        Mono<MasterProfile> byIdentityNo = hasText(incomingIdNo)
                ? Mono.defer(() -> pickUnique(candidates,
                        c -> incomingIdNo.equals(IdentityUtils.normalizeIdentityNo(c.getIdentityNo())), "identityNo"))
                : Mono.empty();

        // 3. MST
        String incomingTax = IdentityUtils.normalizeIdentityNo(data.getTaxCode());
        Mono<MasterProfile> byTaxCode = hasText(incomingTax)
                ? Mono.defer(() -> pickUnique(candidates,
                        c -> incomingTax.equals(IdentityUtils.normalizeIdentityNo(c.getTaxCode())), "taxCode"))
                : Mono.empty();

        // 4. Khóa duy nhất do nguồn cấp
        Mono<MasterProfile> byTypedId = Mono.defer(() -> pickUniqueAsync(candidates,
                c -> matchedByUniqueTypedId(data, c), "typed identifier"));

        return bySourceLink
                .switchIfEmpty(byIdentityNo)
                .switchIfEmpty(byTaxCode)
                .switchIfEmpty(byTypedId);
    }

    /** Trả candidate duy nhất thoả {@code matcher}; empty nếu không có hoặc có nhiều hơn một. */
    private Mono<MasterProfile> pickUnique(List<MasterProfile> candidates,
                                           Predicate<MasterProfile> matcher, String rule) {
        return pickUniqueAsync(candidates, c -> Mono.just(matcher.test(c)), rule);
    }

    /** Như {@link #pickUnique} nhưng {@code matcher} phải truy vấn DB nên trả {@code Mono<Boolean>}. */
    private Mono<MasterProfile> pickUniqueAsync(List<MasterProfile> candidates,
                                                Function<MasterProfile, Mono<Boolean>> matcher, String rule) {
        return Flux.fromIterable(candidates)
                .filterWhen(matcher::apply)
                .collectList()
                .flatMap(matched -> {
                    if (matched.size() == 1) {
                        log.info("ProfileMergeDecisionService - {} candidates → tách được bằng {}: profileId={}",
                                candidates.size(), rule, matched.get(0).getId());
                        return Mono.just(matched.get(0));
                    }
                    if (matched.size() > 1) {
                        log.info("ProfileMergeDecisionService - {} candidates cùng khớp {} → vẫn là xung đột thật",
                                matched.size(), rule);
                    }
                    return Mono.empty();
                });
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
