package vn.vnpost.cdp.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import vn.vnpost.cdp.ingestion.dto.MergeDecisionResult;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.ingestion.dto.ProfileIngestionMessage;
import vn.vnpost.cdp.ingestion.enums.MergeDecision;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.entity.ProfileSourceRecord;
import vn.vnpost.cdp.profile.repository.ProfileSourceRecordRepository;
import vn.vnpost.cdp.profile.service.ProfileMergePostProcessingService;
import vn.vnpost.cdp.profile.service.match.ProfileMatchCandidateService;
import vn.vnpost.cdp.common.security.SecurityUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reactive port của orchestrator gốc. Toàn bộ chuỗi (lưu source record → normalize → match →
 * decide → execute) được bọc trong MỘT {@code @Transactional} duy nhất (giống class-level
 * {@code @Transactional} của bản gốc — reactive transaction propagate qua Reactor Context nên
 * các {@code @Transactional} method của {@code ProfileMergeExecutorService} được gọi bên trong
 * cùng chuỗi Mono này sẽ JOIN vào đúng 1 transaction, giống {@code Propagation.REQUIRED} mặc định).
 *
 * <p><b>Lưu ý rủi ro giữ nguyên từ bản gốc:</b> nếu {@code executeDecision} lỗi, khối
 * {@code handleError} cố lưu {@code sourceRecord} với trạng thái ERROR TRONG CÙNG transaction
 * đã có thể bị đánh dấu rollback-only — bản gốc (JPA, class-level {@code @Transactional}) có
 * CHÍNH XÁC rủi ro này (save trong catch block có thể không thực sự commit). Không tự ý sửa —
 * xem báo cáo cuối để biết thêm.</p>
 */
@Slf4j
@Service
public class ProfileIngestionServiceImpl implements ProfileIngestionService {

    private final ProfileSourceRecordRepository sourceRecordRepository;
    private final ProfileNormalizationService normalizationService;
    private final ProfileMatchingService matchingService;
    private final ProfileMergeDecisionService mergeDecisionService;
    private final ProfileMergeExecutorService mergeExecutorService;
    private final ProfileMatchCandidateService matchCandidateService;
    private final ProfileMergePostProcessingService postProcessingService;

    public ProfileIngestionServiceImpl(
            ProfileSourceRecordRepository sourceRecordRepository,
            ProfileNormalizationService normalizationService,
            ProfileMatchingService matchingService,
            ProfileMergeDecisionService mergeDecisionService,
            ProfileMergeExecutorService mergeExecutorService,
            ProfileMatchCandidateService matchCandidateService,
            ProfileMergePostProcessingService postProcessingService) {
        this.sourceRecordRepository = sourceRecordRepository;
        this.normalizationService = normalizationService;
        this.matchingService = matchingService;
        this.mergeDecisionService = mergeDecisionService;
        this.mergeExecutorService = mergeExecutorService;
        this.matchCandidateService = matchCandidateService;
        this.postProcessingService = postProcessingService;
    }

    @Override
    @Transactional
    public Mono<Void> process(ProfileIngestionMessage message) {
        log.info("ProfileIngestionServiceImpl - process: messageId={}, sourceSystem={}, sourceCustomerId={}",
                message.getMessageId(), message.getSourceSystem(), message.getSourceCustomerId());

        // 1. Save source record with PENDING status
        ProfileSourceRecord sourceRecord = new ProfileSourceRecord();
        sourceRecord.setSourceSystem(message.getSourceSystem());
        sourceRecord.setSourceCustomerId(message.getSourceCustomerId());
        sourceRecord.setSourceEventId(message.getMessageId());
        sourceRecord.setRawPayload(message.getPayload());
        sourceRecord.setReceivedAt(message.getReceivedAt() != null ? message.getReceivedAt() : LocalDateTime.now());
        sourceRecord.setMergeStatus((short) 0); // PENDING

        return SecurityUtils.getCurrentUsername().defaultIfEmpty("kafka-consumer")
                .flatMap(createdBy -> {
                    sourceRecord.setCreatedBy(createdBy);
                    return sourceRecordRepository.save(sourceRecord);
                })
                .doOnNext(saved -> log.debug("ProfileIngestionServiceImpl - saved sourceRecord id={}", saved.getId()))
                .flatMap(saved -> processSaved(saved, message)
                        .onErrorResume(ex -> handleError(saved, message, ex)));
    }

    private Mono<Void> processSaved(ProfileSourceRecord sourceRecord, ProfileIngestionMessage message) {
        // 2. Normalize profile data
        NormalizedProfileData normalizedData = normalizationService.normalize(message);
        log.debug("ProfileIngestionServiceImpl - normalized: identityNo={}, phone={}, email={}",
                normalizedData.getIdentityNo(), normalizedData.getPhone(), normalizedData.getEmail());

        // 3. Resolve identityKey and update source record
        String identityKey = resolveIdentityKey(normalizedData);
        sourceRecord.setIdentityKey(identityKey);
        sourceRecord.setNormalizedPayload(normalizedData.getNormalizedPayload());

        return sourceRecordRepository.save(sourceRecord)
                // 4. Find matching candidates
                .then(matchingService.findCandidateProfiles(normalizedData))
                .flatMap(candidates -> {
                    log.info("ProfileIngestionServiceImpl - found {} candidate(s) for messageId={}",
                            candidates.size(), message.getMessageId());

                    // 5. Decide merge strategy
                    return mergeDecisionService.decide(normalizedData, candidates)
                            .flatMap(decisionResult -> {
                                MergeDecision decision = decisionResult.decision();
                                log.info("ProfileIngestionServiceImpl - merge decision={} targetProfileId={} " +
                                                "for messageId={}",
                                        decision,
                                        decisionResult.target() != null ? decisionResult.target().getId() : null,
                                        message.getMessageId());

                                // 6. Execute merge
                                return executeDecision(decisionResult, sourceRecord, normalizedData, candidates, message)
                                        .doOnSuccess(v -> log.info("ProfileIngestionServiceImpl - completed " +
                                                        "processing messageId={}, decision={}",
                                                message.getMessageId(), decision));
                            });
                });
    }

    private Mono<Void> executeDecision(MergeDecisionResult decisionResult, ProfileSourceRecord sourceRecord,
                                        NormalizedProfileData normalizedData, List<MasterProfile> candidates,
                                        ProfileIngestionMessage message) {
        MergeDecision decision = decisionResult.decision();
        return switch (decision) {
            case CREATE_NEW_PROFILE -> {
                log.info("ProfileIngestionServiceImpl - creating new profile for messageId={}", message.getMessageId());
                yield mergeExecutorService.createNewProfile(sourceRecord, normalizedData)
                        .doOnNext(profile -> firePostProcessing(profile, "CREATE"))
                        .then();
            }
            case AUTO_MERGE -> {
                // Phải dùng target do decide() chọn, KHÔNG phải candidates.get(0): khi có nhiều
                // candidate, decide() tách bằng khóa mạnh và hồ sơ được chọn có thể ở index khác.
                MasterProfile target = decisionResult.target();
                log.info("ProfileIngestionServiceImpl - auto-merging with profileId={} for messageId={}",
                        target.getId(), message.getMessageId());
                yield mergeExecutorService.autoMerge(sourceRecord, normalizedData, target)
                        .doOnNext(profile -> firePostProcessing(profile, "UPDATE"))
                        .then();
            }
            case CONFLICT -> {
                log.info("ProfileIngestionServiceImpl - multiple candidates CONFLICT for messageId={}",
                        message.getMessageId());
                yield mergeExecutorService.createMultipleCandidateConflict(sourceRecord, normalizedData, candidates);
            }
            case NEED_REVIEW -> {
                log.info("ProfileIngestionServiceImpl - field-level NEED_REVIEW for messageId={}",
                        message.getMessageId());
                yield mergeExecutorService.createFieldLevelNeedReview(sourceRecord, normalizedData,
                        decisionResult.target());
            }
            case CREATE_MATCH_CANDIDATE -> {
                log.info("ProfileIngestionServiceImpl - CREATE_MATCH_CANDIDATE for messageId={}", message.getMessageId());
                MasterProfile existingProfile = decisionResult.target();
                yield mergeExecutorService.createProfileForReview(sourceRecord, normalizedData)
                        .flatMap(newProfile -> matchCandidateService.createCandidateBetweenProfiles(
                                        existingProfile.getId(), newProfile.getId(), normalizedData, sourceRecord)
                                .then(Mono.defer(() -> {
                                    sourceRecord.setMasterProfileId(newProfile.getId());
                                    sourceRecord.setMergeStatus((short) 4); // NEED_REVIEW
                                    sourceRecord.setProcessedAt(LocalDateTime.now());
                                    return sourceRecordRepository.save(sourceRecord);
                                }))
                                .doOnSuccess(v -> log.info("ProfileIngestionServiceImpl - CREATE_MATCH_CANDIDATE " +
                                                "completed: existingProfileId={}, newProfileId={}, messageId={}",
                                        existingProfile.getId(), newProfile.getId(), message.getMessageId())))
                        .then();
            }
            case REJECT -> {
                log.warn("ProfileIngestionServiceImpl - REJECT messageId={}: no usable identity or unknown source",
                        message.getMessageId());
                sourceRecord.setMergeStatus((short) 3); // REJECTED
                sourceRecord.setErrorMessage("Rejected: " + buildRejectReason(normalizedData));
                sourceRecord.setProcessedAt(LocalDateTime.now());
                yield sourceRecordRepository.save(sourceRecord).then();
            }
            default -> {
                log.warn("ProfileIngestionServiceImpl - unhandled decision={} for messageId={}", decision, message.getMessageId());
                sourceRecord.setMergeStatus((short) 5); // ERROR
                sourceRecord.setErrorMessage("Unhandled merge decision: " + decision);
                sourceRecord.setProcessedAt(LocalDateTime.now());
                yield sourceRecordRepository.save(sourceRecord).then();
            }
        };
    }

    /**
     * Sync Unomi + detect match candidate SAU khi profile đã lưu — chạy tách rời (subscribe độc
     * lập, không nối vào chuỗi Mono chính) để không nằm trong transaction chính và không làm luồng
     * ingestion chính fail nếu việc này lỗi, mô phỏng {@code @Async @TransactionalEventListener
     * (AFTER_COMMIT)} của bản gốc.
     */
    private void firePostProcessing(MasterProfile profile, String syncType) {
        postProcessingService.handle(profile, syncType)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        ex -> log.warn("ProfileIngestionServiceImpl - post-processing failed for profileId={}: {}",
                                profile.getId(), ex.getMessage())
                );
    }

    private Mono<Void> handleError(ProfileSourceRecord sourceRecord, ProfileIngestionMessage message, Throwable ex) {
        log.error("ProfileIngestionServiceImpl - ERROR processing messageId={}: {}",
                message.getMessageId(), ex.getMessage(), ex);

        // 7. On exception: mark source record as ERROR
        sourceRecord.setMergeStatus((short) 5); // ERROR
        sourceRecord.setErrorMessage(truncate(ex.getMessage(), 2000));
        sourceRecord.setProcessedAt(LocalDateTime.now());

        return sourceRecordRepository.save(sourceRecord)
                .then()
                .onErrorResume(saveEx -> {
                    log.error("ProfileIngestionServiceImpl - CRITICAL: could not save error state for sourceRecord id={}",
                            sourceRecord.getId(), saveEx);
                    return Mono.empty();
                });
    }

    // =====================================================================
    // PRIVATE HELPERS
    // =====================================================================

    private String resolveIdentityKey(NormalizedProfileData data) {
        if (StringUtils.hasText(data.getIdentityNo())) {
            return "identityNo:" + data.getIdentityNo();
        }
        if (StringUtils.hasText(data.getPhone())) {
            return "phone:" + data.getPhone();
        }
        if (StringUtils.hasText(data.getEmail())) {
            return "email:" + data.getEmail();
        }
        if (StringUtils.hasText(data.getSourceCustomerId())) {
            return "sourceCustomerId:" + data.getSourceCustomerId();
        }
        return null;
    }

    private String buildRejectReason(NormalizedProfileData data) {
        // Giữ đúng bộ khóa mà ProfileMergeDecisionService.hasUsableIdentity() dùng để REJECT,
        // nếu không thông báo lỗi sẽ chỉ ra sai nguyên nhân.
        boolean hasIdentity = StringUtils.hasText(data.getIdentityNo())
                || StringUtils.hasText(data.getPhone())
                || StringUtils.hasText(data.getEmail())
                || StringUtils.hasText(data.getSourceCustomerId())
                || StringUtils.hasText(data.getTaxCode())
                || StringUtils.hasText(data.getPostId())
                || StringUtils.hasText(data.getKhlCode())
                || StringUtils.hasText(data.getCrmId())
                || StringUtils.hasText(data.getAppUserId())
                || StringUtils.hasText(data.getPaymentId());
        if (!hasIdentity) {
            return "No usable identity fields (identityNo, phone, email, sourceCustomerId, taxCode, "
                    + "postId, khlCode, crmId, appUserId, paymentId are all blank)";
        }
        return "Unknown or invalid source system: " + data.getSourceSystem();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
