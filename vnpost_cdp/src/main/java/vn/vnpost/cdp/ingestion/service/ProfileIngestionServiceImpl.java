package vn.vnpost.cdp.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.ingestion.dto.ProfileIngestionMessage;
import vn.vnpost.cdp.ingestion.enums.MergeDecision;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.entity.ProfileSourceRecord;
import vn.vnpost.cdp.profile.repository.ProfileSourceRecordRepository;
import vn.vnpost.cdp.profile.service.match.ProfileMatchCandidateService;
import vn.vnpost.cdp.security.SecurityUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional
public class ProfileIngestionServiceImpl implements ProfileIngestionService {

    private final ProfileSourceRecordRepository sourceRecordRepository;
    private final ProfileNormalizationService normalizationService;
    private final ProfileMatchingService matchingService;
    private final ProfileMergeDecisionService mergeDecisionService;
    private final ProfileMergeExecutorService mergeExecutorService;
    private final ProfileMatchCandidateService matchCandidateService;

    public ProfileIngestionServiceImpl(
            ProfileSourceRecordRepository sourceRecordRepository,
            ProfileNormalizationService normalizationService,
            ProfileMatchingService matchingService,
            ProfileMergeDecisionService mergeDecisionService,
            ProfileMergeExecutorService mergeExecutorService,
            ProfileMatchCandidateService matchCandidateService) {
        this.sourceRecordRepository = sourceRecordRepository;
        this.normalizationService = normalizationService;
        this.matchingService = matchingService;
        this.mergeDecisionService = mergeDecisionService;
        this.mergeExecutorService = mergeExecutorService;
        this.matchCandidateService = matchCandidateService;
    }

    @Override
    public void process(ProfileIngestionMessage message) {
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
        sourceRecord.setCreatedBy(SecurityUtils.getCurrentUsername().orElse("kafka-consumer"));
        sourceRecord = sourceRecordRepository.save(sourceRecord);
        log.debug("ProfileIngestionServiceImpl - saved sourceRecord id={}", sourceRecord.getId());

        try {
            // 2. Normalize profile data
            NormalizedProfileData normalizedData = normalizationService.normalize(message);
            log.debug("ProfileIngestionServiceImpl - normalized: identityNo={}, phone={}, email={}",
                    normalizedData.getIdentityNo(), normalizedData.getPhone(), normalizedData.getEmail());

            // 3. Resolve identityKey and update source record
            String identityKey = resolveIdentityKey(normalizedData);
            sourceRecord.setIdentityKey(identityKey);
            sourceRecord.setNormalizedPayload(normalizedData.getNormalizedPayload());
            sourceRecordRepository.save(sourceRecord);

            // 4. Find matching candidates
            List<MasterProfile> candidates = matchingService.findCandidateProfiles(normalizedData);
            log.info("ProfileIngestionServiceImpl - found {} candidate(s) for messageId={}",
                    candidates.size(), message.getMessageId());

            // 5. Decide merge strategy
            MergeDecision decision = mergeDecisionService.decide(normalizedData, candidates);
            log.info("ProfileIngestionServiceImpl - merge decision={} for messageId={}",
                    decision, message.getMessageId());

            // 6. Execute merge
            switch (decision) {
                case CREATE_NEW_PROFILE -> {
                    log.info("ProfileIngestionServiceImpl - creating new profile for messageId={}", message.getMessageId());
                    mergeExecutorService.createNewProfile(sourceRecord, normalizedData);
                }
                case AUTO_MERGE -> {
                    MasterProfile target = candidates.get(0);
                    log.info("ProfileIngestionServiceImpl - auto-merging with profileId={} for messageId={}",
                            target.getId(), message.getMessageId());
                    mergeExecutorService.autoMerge(sourceRecord, normalizedData, target);
                }
                case CONFLICT -> {
                    log.info("ProfileIngestionServiceImpl - multiple candidates CONFLICT for messageId={}",
                            message.getMessageId());
                    mergeExecutorService.createMultipleCandidateConflict(sourceRecord, normalizedData, candidates);
                }
                case NEED_REVIEW -> {
                    log.info("ProfileIngestionServiceImpl - field-level NEED_REVIEW for messageId={}",
                            message.getMessageId());
                    mergeExecutorService.createFieldLevelNeedReview(sourceRecord, normalizedData, candidates.get(0));
                }
                case CREATE_MATCH_CANDIDATE -> {

                    log.info("ProfileIngestionServiceImpl - CREATE_MATCH_CANDIDATE for messageId={}",
                            message.getMessageId());
                    MasterProfile existingProfile = candidates.get(0);
                    MasterProfile newProfile = mergeExecutorService.createProfileForReview(
                            sourceRecord, normalizedData);
                    matchCandidateService.createCandidateBetweenProfiles(
                            existingProfile.getId(),
                            newProfile.getId(),
                            normalizedData,
                            sourceRecord);
                    sourceRecord.setMasterProfileId(newProfile.getId());
                    sourceRecord.setMergeStatus((short) 4); // NEED_REVIEW
                    sourceRecord.setProcessedAt(LocalDateTime.now());
                    sourceRecordRepository.save(sourceRecord);
                    log.info("ProfileIngestionServiceImpl - CREATE_MATCH_CANDIDATE completed: " +
                                    "existingProfileId={}, newProfileId={}, messageId={}",
                            existingProfile.getId(), newProfile.getId(), message.getMessageId());
                }
                case REJECT -> {
                    log.warn("ProfileIngestionServiceImpl - REJECT messageId={}: no usable identity or unknown source",
                            message.getMessageId());
                    sourceRecord.setMergeStatus((short) 3); // REJECTED
                    sourceRecord.setErrorMessage("Rejected: " + buildRejectReason(normalizedData));
                    sourceRecord.setProcessedAt(LocalDateTime.now());
                    sourceRecordRepository.save(sourceRecord);
                }
                default -> {
                    log.warn("ProfileIngestionServiceImpl - unhandled decision={} for messageId={}", decision, message.getMessageId());
                    sourceRecord.setMergeStatus((short) 5); // ERROR
                    sourceRecord.setErrorMessage("Unhandled merge decision: " + decision);
                    sourceRecord.setProcessedAt(LocalDateTime.now());
                    sourceRecordRepository.save(sourceRecord);
                }
            }

            log.info("ProfileIngestionServiceImpl - completed processing messageId={}, decision={}",
                    message.getMessageId(), decision);

        } catch (Exception ex) {
            log.error("ProfileIngestionServiceImpl - ERROR processing messageId={}: {}",
                    message.getMessageId(), ex.getMessage(), ex);
            // 7. On exception: mark source record as ERROR
            try {
                sourceRecord.setMergeStatus((short) 5); // ERROR
                sourceRecord.setErrorMessage(truncate(ex.getMessage(), 2000));
                sourceRecord.setProcessedAt(LocalDateTime.now());
                sourceRecordRepository.save(sourceRecord);
            } catch (Exception saveEx) {
                log.error("ProfileIngestionServiceImpl - CRITICAL: could not save error state for sourceRecord id={}",
                        sourceRecord.getId(), saveEx);
            }
        }
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
        boolean hasIdentity = StringUtils.hasText(data.getIdentityNo())
                || StringUtils.hasText(data.getPhone())
                || StringUtils.hasText(data.getEmail())
                || StringUtils.hasText(data.getSourceCustomerId());
        if (!hasIdentity) {
            return "No usable identity fields (identityNo, phone, email, sourceCustomerId are all blank)";
        }
        return "Unknown or invalid source system: " + data.getSourceSystem();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
