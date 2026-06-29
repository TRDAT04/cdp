package vn.vnpost.cdp.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.profile.entity.*;
import vn.vnpost.cdp.profile.repository.*;
import vn.vnpost.cdp.profile.service.match.ProfileMatchCandidateService;
import vn.vnpost.cdp.security.SecurityUtils;
import vn.vnpost.cdp.unomi.service.UnomiService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProfileMergeExecutorService {

    private static final String CRM = "CRM";
    private static final List<String> CRM_OWNED_FIELDS =
            List.of("fullName", "phone", "email", "identityNo", "gender",
                    "dateOfBirth", "customerType", "provinceCode", "provinceName",
                    "unitCode", "unitName");

    private final MasterProfileRepository masterProfileRepository;
    private final ProfileSourceRecordRepository sourceRecordRepository;
    private final ProfileIdentityLinkRepository identityLinkRepository;
    private final ProfileAttributeValueRepository attributeValueRepository;
    private final ProfileChangeLogRepository changeLogRepository;
    private final ProfileMergeConflictRepository conflictRepository;
    private final ProfileUnomiSyncLogRepository unomiSyncLogRepository;
    private final UnomiService unomiService;
    private final ObjectMapper objectMapper;
    private final ProfileMatchCandidateService matchCandidateService;

    public ProfileMergeExecutorService(
            MasterProfileRepository masterProfileRepository,
            ProfileSourceRecordRepository sourceRecordRepository,
            ProfileIdentityLinkRepository identityLinkRepository,
            ProfileAttributeValueRepository attributeValueRepository,
            ProfileChangeLogRepository changeLogRepository,
            ProfileMergeConflictRepository conflictRepository,
            ProfileUnomiSyncLogRepository unomiSyncLogRepository,
            UnomiService unomiService,
            ObjectMapper objectMapper,
            ProfileMatchCandidateService matchCandidateService) {
        this.masterProfileRepository = masterProfileRepository;
        this.sourceRecordRepository = sourceRecordRepository;
        this.identityLinkRepository = identityLinkRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.changeLogRepository = changeLogRepository;
        this.conflictRepository = conflictRepository;
        this.unomiSyncLogRepository = unomiSyncLogRepository;
        this.unomiService = unomiService;
        this.objectMapper = objectMapper;
        this.matchCandidateService = matchCandidateService;
    }

    // =====================================================================
    // CREATE NEW PROFILE
    // =====================================================================

    @Transactional
    public MasterProfile createNewProfile(ProfileSourceRecord sourceRecord, NormalizedProfileData data) {
        log.info("ProfileMergeExecutorService - createNewProfile: sourceSystem={}, sourceCustomerId={}",
                data.getSourceSystem(), data.getSourceCustomerId());

        // 1. Generate profileCode
        String profileCode = "MP_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        // 2. Create and save MasterProfile
        MasterProfile profile = new MasterProfile();
        profile.setProfileCode(profileCode);
        profile.setFullName(data.getFullName());
        profile.setPhone(data.getPhone());
        profile.setEmail(data.getEmail());
        profile.setIdentityNo(data.getIdentityNo());
        profile.setGender(data.getGender());
        profile.setDateOfBirth(data.getDateOfBirth());
        profile.setCustomerType(data.getCustomerType());
        profile.setProvinceCode(data.getProvinceCode());
        profile.setProvinceName(data.getProvinceName());
        profile.setUnitCode(data.getUnitCode());
        profile.setUnitName(data.getUnitName());
        profile.setLastMergedAt(LocalDateTime.now());
        profile.setStatus((short) 1);
        profile.setSourceSummary(Map.of("initialSource", data.getSourceSystem(),
                "sourceCustomerId", data.getSourceCustomerId()));
        profile = masterProfileRepository.save(profile);
        log.info("ProfileMergeExecutorService - created MasterProfile id={}, profileCode={}",
                profile.getId(), profile.getProfileCode());

        // 3. Create identity link
        createIdentityLink(profile.getId(), data, true);

        // 4 & 5. Save attribute values (all isSelected=true for first source)
        saveAttributeValues(profile.getId(), sourceRecord.getId(), data, true);

        // 6. Write change logs for all fields
        writeChangeLogs(profile.getId(), sourceRecord.getId(), data.getSourceSystem(), data,
                null, "AUTO_MERGE", "CREATE_NEW_PROFILE",
                "New master profile created from source data");

        // 7. Update source record
        sourceRecord.setMasterProfileId(profile.getId());
        sourceRecord.setMergeStatus((short) 1); // MERGED
        sourceRecord.setProcessedAt(LocalDateTime.now());
        sourceRecordRepository.save(sourceRecord);

        // 8. Sync to Unomi and write log
        syncToUnomi(profile, sourceRecord, "CREATE");

        // 9. Trigger match candidate detection for newly created profile
        try {
            matchCandidateService.detectAndCreateCandidatesForProfile(profile.getId());
        } catch (Exception ex) {
            log.warn("ProfileMergeExecutorService - match candidate detection failed for profile {}: {}",
                    profile.getId(), ex.getMessage());
        }

        return profile;
    }

    // =====================================================================
    // AUTO MERGE
    // =====================================================================

    @Transactional
    public MasterProfile autoMerge(ProfileSourceRecord sourceRecord, NormalizedProfileData data,
                                   MasterProfile targetProfile) {
        log.info("ProfileMergeExecutorService - autoMerge: sourceSystem={}, targetProfileId={}",
                data.getSourceSystem(), targetProfile.getId());

        // 1. Ensure identity link exists
        boolean linkedAlready = identityLinkRepository
                .findBySourceSystemAndSourceCustomerId(data.getSourceSystem(), data.getSourceCustomerId())
                .isPresent();
        if (!linkedAlready) {
            createIdentityLink(targetProfile.getId(), data, false);
        }

        // 2. Save incoming attribute values (not selected by default, selection handled below)
        saveAttributeValues(targetProfile.getId(), sourceRecord.getId(), data, false);

        boolean isCrm = CRM.equalsIgnoreCase(data.getSourceSystem());

        // 3. Apply merge rules: CRM can update all CRM-owned fields; CMS only its own fields
        boolean profileUpdated = false;

        if (isCrm) {
            // CRM updates: update if incoming has a value
            if (StringUtils.hasText(data.getFullName())
                    && !data.getFullName().equals(targetProfile.getFullName())) {
                writeChangeLog(targetProfile.getId(), sourceRecord.getId(), data.getSourceSystem(),
                        "fullName", targetProfile.getFullName(), data.getFullName(),
                        null, data.getSourceSystem(), "SOURCE_PRIORITY",
                        "CRM update: fullName");
                targetProfile.setFullName(data.getFullName());
                profileUpdated = true;
            }
            if (StringUtils.hasText(data.getPhone())
                    && !data.getPhone().equals(targetProfile.getPhone())) {
                writeChangeLog(targetProfile.getId(), sourceRecord.getId(), data.getSourceSystem(),
                        "phone", targetProfile.getPhone(), data.getPhone(),
                        null, data.getSourceSystem(), "SOURCE_PRIORITY", "CRM update: phone");
                targetProfile.setPhone(data.getPhone());
                profileUpdated = true;
            }
            if (StringUtils.hasText(data.getEmail())
                    && !data.getEmail().equals(targetProfile.getEmail())) {
                writeChangeLog(targetProfile.getId(), sourceRecord.getId(), data.getSourceSystem(),
                        "email", targetProfile.getEmail(), data.getEmail(),
                        null, data.getSourceSystem(), "SOURCE_PRIORITY", "CRM update: email");
                targetProfile.setEmail(data.getEmail());
                profileUpdated = true;
            }
            if (StringUtils.hasText(data.getIdentityNo())
                    && !data.getIdentityNo().equals(targetProfile.getIdentityNo())) {
                writeChangeLog(targetProfile.getId(), sourceRecord.getId(), data.getSourceSystem(),
                        "identityNo", targetProfile.getIdentityNo(), data.getIdentityNo(),
                        null, data.getSourceSystem(), "SOURCE_PRIORITY", "CRM update: identityNo");
                targetProfile.setIdentityNo(data.getIdentityNo());
                profileUpdated = true;
            }
            if (StringUtils.hasText(data.getGender())
                    && !data.getGender().equals(targetProfile.getGender())) {
                writeChangeLog(targetProfile.getId(), sourceRecord.getId(), data.getSourceSystem(),
                        "gender", targetProfile.getGender(), data.getGender(),
                        null, data.getSourceSystem(), "SOURCE_PRIORITY", "CRM update: gender");
                targetProfile.setGender(data.getGender());
                profileUpdated = true;
            }
            if (data.getDateOfBirth() != null
                    && !data.getDateOfBirth().equals(targetProfile.getDateOfBirth())) {
                writeChangeLog(targetProfile.getId(), sourceRecord.getId(), data.getSourceSystem(),
                        "dateOfBirth",
                        targetProfile.getDateOfBirth() != null ? targetProfile.getDateOfBirth().toString() : null,
                        data.getDateOfBirth().toString(),
                        null, data.getSourceSystem(), "SOURCE_PRIORITY", "CRM update: dateOfBirth");
                targetProfile.setDateOfBirth(data.getDateOfBirth());
                profileUpdated = true;
            }
            if (StringUtils.hasText(data.getCustomerType())
                    && !data.getCustomerType().equals(targetProfile.getCustomerType())) {
                targetProfile.setCustomerType(data.getCustomerType());
                profileUpdated = true;
            }
            if (StringUtils.hasText(data.getProvinceCode())
                    && !data.getProvinceCode().equals(targetProfile.getProvinceCode())) {
                targetProfile.setProvinceCode(data.getProvinceCode());
                targetProfile.setProvinceName(data.getProvinceName());
                profileUpdated = true;
            }
            if (StringUtils.hasText(data.getUnitCode())
                    && !data.getUnitCode().equals(targetProfile.getUnitCode())) {
                targetProfile.setUnitCode(data.getUnitCode());
                targetProfile.setUnitName(data.getUnitName());
                profileUpdated = true;
            }
        } else {
            // CMS: only update fields that CRM does not own or master has no value yet
            // Also update lastVisitAt always (CMS-owned)
            if (StringUtils.hasText(data.getPhone()) && !StringUtils.hasText(targetProfile.getPhone())) {
                targetProfile.setPhone(data.getPhone());
                profileUpdated = true;
            }
            if (StringUtils.hasText(data.getEmail()) && !StringUtils.hasText(targetProfile.getEmail())) {
                targetProfile.setEmail(data.getEmail());
                profileUpdated = true;
            }
        }

        if (profileUpdated) {
            targetProfile.setLastMergedAt(LocalDateTime.now());
            masterProfileRepository.save(targetProfile);
        }

        // 4. Update source record
        sourceRecord.setMasterProfileId(targetProfile.getId());
        sourceRecord.setMergeStatus((short) 1); // MERGED
        sourceRecord.setProcessedAt(LocalDateTime.now());
        sourceRecordRepository.save(sourceRecord);

        // 5. Sync to Unomi
        syncToUnomi(targetProfile, sourceRecord, "UPDATE");

        // 6. Trigger match candidate detection for updated profile
        try {
            matchCandidateService.detectAndCreateCandidatesForProfile(targetProfile.getId());
        } catch (Exception ex) {
            log.warn("ProfileMergeExecutorService - match candidate detection failed for profile {}: {}",
                    targetProfile.getId(), ex.getMessage());
        }

        return targetProfile;
    }

    // =====================================================================
    // MULTIPLE-CANDIDATE CONFLICT  (MergeDecision.CONFLICT)
    // =====================================================================

    /**
     * Handles the case where more than one master profile matched the incoming data.
     * Creates a single PROFILE_MATCH conflict record and marks the source record CONFLICT.
     * Does NOT touch any master profile, does NOT create identity links, does NOT sync Unomi.
     */
    @Transactional
    public void createMultipleCandidateConflict(ProfileSourceRecord sourceRecord,
                                                NormalizedProfileData data,
                                                List<MasterProfile> candidates) {
        log.info("ProfileMergeExecutorService - createMultipleCandidateConflict: candidates={}",
                candidates.size());

        MasterProfile first = candidates.get(0);
        String candidateIds = candidates.stream()
                .map(p -> p.getId().toString()).collect(Collectors.joining(","));

        ProfileMergeConflict conflict = new ProfileMergeConflict();
        conflict.setMasterProfileId(first.getId()); // reference only, not selected target
        conflict.setSourceRecordId(sourceRecord.getId());
        conflict.setPropertyName("PROFILE_MATCH");
        conflict.setConflictReason("Multiple candidate profiles matched incoming data");
        conflict.setCurrentValue(candidateIds);
        conflict.setIncomingValue(buildIdentitySummary(data));
        conflict.setIncomingSource(data.getSourceSystem());
        conflict.setResolutionStatus((short) 0); // OPEN
        conflictRepository.save(conflict);

        ProfileChangeLog cl = new ProfileChangeLog();
        cl.setMasterProfileId(first.getId());
        cl.setSourceRecordId(sourceRecord.getId());
        cl.setSourceSystem(data.getSourceSystem());
        cl.setEventType("CONFLICT_DETECTED");
        cl.setPropertyName("PROFILE_MATCH");
        cl.setReason("Multiple candidates found: " + candidateIds);
        cl.setChangedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        cl.setChangedAt(LocalDateTime.now());
        changeLogRepository.save(cl);

        sourceRecord.setMergeStatus((short) 2); // CONFLICT
        sourceRecord.setProcessedAt(LocalDateTime.now());
        sourceRecordRepository.save(sourceRecord);
    }

    // =====================================================================
    // FIELD-LEVEL NEED_REVIEW  (MergeDecision.NEED_REVIEW, single candidate)
    // =====================================================================

    /**
     * Handles the case where a single candidate was found but identity fields conflict
     * (e.g. CMS sends same email as CRM but a different phone).
     *
     * Rules:
     * - Does NOT update master profile fields.
     * - Does NOT create active identity links.
     * - Does NOT sync to Unomi.
     * - Does NOT create profile_match_candidates.
     * - Creates conflict rows ONLY for hard identity fields: phone, email, identityNo.
     * - fullName differences are saved as attribute values only (name formatting / accents differ).
     * - Normalizes phone/email/identityNo before comparing.
     */
    @Transactional
    public void createFieldLevelNeedReview(ProfileSourceRecord sourceRecord,
                                           NormalizedProfileData data,
                                           MasterProfile target) {
        log.info("ProfileMergeExecutorService - createFieldLevelNeedReview: targetProfileId={}, sourceSystem={}",
                target.getId(), data.getSourceSystem());

        // 1. Link source record to the target profile
        sourceRecord.setMasterProfileId(target.getId());

        // 2. Save incoming attributes with appropriate selection flags
        saveAttributeValuesForNeedReview(target.getId(), sourceRecord.getId(), data);

        // 3. Field-level conflicts — only phone, email, identityNo (NOT fullName)
        List<String> conflictingFields = new ArrayList<>();

        String incomingPhone  = normalizePhone(data.getPhone());
        String targetPhone    = normalizePhone(target.getPhone());
        String incomingEmail  = normalizeEmail(data.getEmail());
        String targetEmail    = normalizeEmail(target.getEmail());
        String incomingIdNo   = normalizeText(data.getIdentityNo());
        String targetIdNo     = normalizeText(target.getIdentityNo());

        if (StringUtils.hasText(incomingPhone) && StringUtils.hasText(targetPhone)
                && !incomingPhone.equals(targetPhone)) {
            createFieldConflict(sourceRecord, target, "phone",
                    target.getPhone(), data.getPhone(), data.getSourceSystem(),
                    "Incoming phone differs from selected phone");
            conflictingFields.add("phone");
        }
        if (StringUtils.hasText(incomingEmail) && StringUtils.hasText(targetEmail)
                && !incomingEmail.equals(targetEmail)) {
            createFieldConflict(sourceRecord, target, "email",
                    target.getEmail(), data.getEmail(), data.getSourceSystem(),
                    "Incoming email differs from selected email");
            conflictingFields.add("email");
        }
        if (StringUtils.hasText(incomingIdNo) && StringUtils.hasText(targetIdNo)
                && !incomingIdNo.equals(targetIdNo)) {
            createFieldConflict(sourceRecord, target, "identityNo",
                    target.getIdentityNo(), data.getIdentityNo(), data.getSourceSystem(),
                    "Incoming identityNo differs from selected identityNo");
            conflictingFields.add("identityNo");
        }
        // fullName is intentionally excluded — name differences (accents, formatting) are
        // saved as attribute values only, not treated as hard conflicts.

        if (!conflictingFields.isEmpty()) {
            ProfileChangeLog cl = new ProfileChangeLog();
            cl.setMasterProfileId(target.getId());
            cl.setSourceRecordId(sourceRecord.getId());
            cl.setSourceSystem(data.getSourceSystem());
            cl.setEventType("CONFLICT_DETECTED");
            cl.setReason("Conflicting fields: " + String.join(", ", conflictingFields));
            cl.setChangedBy(SecurityUtils.getCurrentUsername().orElse("system"));
            cl.setChangedAt(LocalDateTime.now());
            changeLogRepository.save(cl);
        }

        // 4. Update source record
        sourceRecord.setMergeStatus((short) 4); // NEED_REVIEW
        sourceRecord.setProcessedAt(LocalDateTime.now());
        sourceRecordRepository.save(sourceRecord);
    }

    // =====================================================================
    // PRIVATE HELPERS
    // =====================================================================

    private void createIdentityLink(Long masterProfileId, NormalizedProfileData data, boolean isPrimary) {
        String identityType;
        String identityValue;
        BigDecimal confidence;

        if (StringUtils.hasText(data.getIdentityNo())) {
            identityType = "IDENTITY_NO";
            identityValue = data.getIdentityNo();
            confidence = BigDecimal.valueOf(100);
        } else if (StringUtils.hasText(data.getPhone())) {
            identityType = "PHONE";
            identityValue = data.getPhone();
            confidence = BigDecimal.valueOf(90);
        } else if (StringUtils.hasText(data.getEmail())) {
            identityType = "EMAIL";
            identityValue = data.getEmail();
            confidence = BigDecimal.valueOf(80);
        } else {
            identityType = "SOURCE_CUSTOMER_ID";
            identityValue = data.getSourceCustomerId();
            confidence = BigDecimal.valueOf(60);
        }

        ProfileIdentityLink link = new ProfileIdentityLink();
        link.setMasterProfileId(masterProfileId);
        link.setSourceSystem(data.getSourceSystem());
        link.setSourceCustomerId(data.getSourceCustomerId());
        link.setIdentityType(identityType);
        link.setIdentityValue(identityValue);
        link.setConfidenceScore(confidence);
        link.setIsPrimary(isPrimary);
        link.setStatus((short) 1);
        link.setLinkedAt(LocalDateTime.now());
        link.setLinkedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        identityLinkRepository.save(link);
    }

    private void saveAttributeValues(Long masterProfileId, Long sourceRecordId,
                                     NormalizedProfileData data, boolean isSelected) {
        LocalDateTime now = LocalDateTime.now();
        List<ProfileAttributeValue> values = new ArrayList<>();

        addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                "fullName", data.getFullName(), isSelected, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                "phone", data.getPhone(), isSelected, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                "email", data.getEmail(), isSelected, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                "identityNo", data.getIdentityNo(), isSelected, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                "gender", data.getGender(), isSelected, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                "customerType", data.getCustomerType(), isSelected, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                "provinceCode", data.getProvinceCode(), isSelected, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                "provinceName", data.getProvinceName(), isSelected, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                "unitCode", data.getUnitCode(), isSelected, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                "unitName", data.getUnitName(), isSelected, now);

        if (data.getDateOfBirth() != null) {
            addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                    "dateOfBirth", data.getDateOfBirth().toString(), isSelected, now);
        }
        if (data.getLastVisitAt() != null) {
            // CMS-owned: always selected for CMS
            boolean lvSelected = "CMS".equalsIgnoreCase(data.getSourceSystem()) || isSelected;
            addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                    "lastVisitAt", data.getLastVisitAt().toString(), lvSelected, now);
        }
        if (data.getInterestedServices() != null && !data.getInterestedServices().isEmpty()) {
            addAttributeValue(values, masterProfileId, sourceRecordId, data.getSourceSystem(),
                    "interestedServices", String.join(",", data.getInterestedServices()),
                    true, now);
        }

        if (!values.isEmpty()) {
            attributeValueRepository.saveAll(values);
        }
    }

    private void addAttributeValue(List<ProfileAttributeValue> list, Long masterProfileId,
                                   Long sourceRecordId, String sourceSystem, String propertyName,
                                   String propertyValue, boolean isSelected, LocalDateTime now) {
        if (!StringUtils.hasText(propertyValue)) return;
        ProfileAttributeValue av = new ProfileAttributeValue();
        av.setMasterProfileId(masterProfileId);
        av.setSourceRecordId(sourceRecordId);
        av.setSourceSystem(sourceSystem);
        av.setPropertyName(propertyName);
        av.setPropertyValue(propertyValue);
        av.setNormalizedValue(propertyValue);
        av.setIsSelected(isSelected);
        av.setReceivedAt(now);
        list.add(av);
    }

    private void writeChangeLogs(Long masterProfileId, Long sourceRecordId, String sourceSystem,
                                 NormalizedProfileData data, String oldSource,
                                 String eventType, String mergeStrategy, String reason) {
        List<ProfileChangeLog> logs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String actor = SecurityUtils.getCurrentUsername().orElse("system");

        addChangeLog(logs, masterProfileId, sourceRecordId, sourceSystem,
                eventType, "fullName", null, data.getFullName(),
                oldSource, sourceSystem, mergeStrategy, reason, actor, now);
        addChangeLog(logs, masterProfileId, sourceRecordId, sourceSystem,
                eventType, "phone", null, data.getPhone(),
                oldSource, sourceSystem, mergeStrategy, reason, actor, now);
        addChangeLog(logs, masterProfileId, sourceRecordId, sourceSystem,
                eventType, "email", null, data.getEmail(),
                oldSource, sourceSystem, mergeStrategy, reason, actor, now);
        addChangeLog(logs, masterProfileId, sourceRecordId, sourceSystem,
                eventType, "identityNo", null, data.getIdentityNo(),
                oldSource, sourceSystem, mergeStrategy, reason, actor, now);
        addChangeLog(logs, masterProfileId, sourceRecordId, sourceSystem,
                eventType, "gender", null, data.getGender(),
                oldSource, sourceSystem, mergeStrategy, reason, actor, now);
        addChangeLog(logs, masterProfileId, sourceRecordId, sourceSystem,
                eventType, "customerType", null, data.getCustomerType(),
                oldSource, sourceSystem, mergeStrategy, reason, actor, now);
        if (data.getDateOfBirth() != null) {
            addChangeLog(logs, masterProfileId, sourceRecordId, sourceSystem,
                    eventType, "dateOfBirth", null, data.getDateOfBirth().toString(),
                    oldSource, sourceSystem, mergeStrategy, reason, actor, now);
        }

        if (!logs.isEmpty()) {
            changeLogRepository.saveAll(logs);
        }
    }

    private void addChangeLog(List<ProfileChangeLog> list, Long masterProfileId, Long sourceRecordId,
                              String sourceSystem, String eventType, String propertyName,
                              String oldValue, String newValue, String oldSource, String newSource,
                              String mergeStrategy, String reason, String changedBy, LocalDateTime changedAt) {
        if (!StringUtils.hasText(newValue)) return;
        ProfileChangeLog cl = new ProfileChangeLog();
        cl.setMasterProfileId(masterProfileId);
        cl.setSourceRecordId(sourceRecordId);
        cl.setSourceSystem(sourceSystem);
        cl.setEventType(eventType);
        cl.setPropertyName(propertyName);
        cl.setOldValue(oldValue);
        cl.setNewValue(newValue);
        cl.setSelectedValue(newValue);
        cl.setOldSource(oldSource);
        cl.setNewSource(newSource);
        cl.setMergeStrategy(mergeStrategy);
        cl.setReason(reason);
        cl.setChangedBy(changedBy);
        cl.setChangedAt(changedAt);
        list.add(cl);
    }

    private void writeChangeLog(Long masterProfileId, Long sourceRecordId, String sourceSystem,
                                String propertyName, String oldValue, String newValue,
                                String oldSource, String newSource, String mergeStrategy, String reason) {
        ProfileChangeLog cl = new ProfileChangeLog();
        cl.setMasterProfileId(masterProfileId);
        cl.setSourceRecordId(sourceRecordId);
        cl.setSourceSystem(sourceSystem);
        cl.setEventType("AUTO_MERGE");
        cl.setPropertyName(propertyName);
        cl.setOldValue(oldValue);
        cl.setNewValue(newValue);
        cl.setSelectedValue(newValue);
        cl.setOldSource(oldSource);
        cl.setNewSource(newSource);
        cl.setMergeStrategy(mergeStrategy);
        cl.setReason(reason);
        cl.setChangedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        cl.setChangedAt(LocalDateTime.now());
        changeLogRepository.save(cl);
    }

    private void createFieldConflict(ProfileSourceRecord sourceRecord, MasterProfile target,
                                     String propertyName, String currentValue, String incomingValue,
                                     String incomingSource, String conflictReason) {
        ProfileMergeConflict conflict = new ProfileMergeConflict();
        conflict.setMasterProfileId(target.getId());
        conflict.setSourceRecordId(sourceRecord.getId());
        conflict.setPropertyName(propertyName);
        conflict.setCurrentValue(currentValue);
        conflict.setIncomingValue(incomingValue);
        conflict.setCurrentSource(CRM);
        conflict.setIncomingSource(incomingSource);
        conflict.setConflictReason(conflictReason != null ? conflictReason
                : incomingSource + " sent a different value for " + propertyName + " than the current CRM value");
        conflict.setResolutionStatus((short) 0); // OPEN
        conflictRepository.save(conflict);
        log.info("ProfileMergeExecutorService - conflict created: profileId={}, field={}",
                target.getId(), propertyName);
    }

    /**
     * Saves attribute values for the NEED_REVIEW flow.
     * Identity fields (phone, email, identityNo, fullName, gender, dateOfBirth, customerType)
     * are saved with isSelected=false so they do not overwrite the master profile's selected values.
     * Behavior/preference fields (interestedServices, lastVisitAt) are saved with isSelected=true
     * because CMS is the source of truth for these.
     */
    private void saveAttributeValuesForNeedReview(Long masterProfileId, Long sourceRecordId,
                                                   NormalizedProfileData data) {
        LocalDateTime now = LocalDateTime.now();
        List<ProfileAttributeValue> values = new ArrayList<>();
        String src = data.getSourceSystem();

        // Identity fields — isSelected=false (CRM owns these; do not overwrite)
        addAttributeValue(values, masterProfileId, sourceRecordId, src, "fullName",    data.getFullName(),    false, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, src, "phone",       data.getPhone(),       false, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, src, "email",       data.getEmail(),       false, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, src, "identityNo",  data.getIdentityNo(),  false, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, src, "gender",      data.getGender(),      false, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, src, "customerType",data.getCustomerType(),false, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, src, "provinceCode",data.getProvinceCode(),false, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, src, "provinceName",data.getProvinceName(),false, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, src, "unitCode",    data.getUnitCode(),    false, now);
        addAttributeValue(values, masterProfileId, sourceRecordId, src, "unitName",    data.getUnitName(),    false, now);
        if (data.getDateOfBirth() != null) {
            addAttributeValue(values, masterProfileId, sourceRecordId, src,
                    "dateOfBirth", data.getDateOfBirth().toString(), false, now);
        }

        // Behavior/preference fields — CMS is the source of truth; save with isSelected=true
        if (data.getLastVisitAt() != null) {
            addAttributeValue(values, masterProfileId, sourceRecordId, src,
                    "lastVisitAt", data.getLastVisitAt().toString(), true, now);
        }
        if (data.getInterestedServices() != null && !data.getInterestedServices().isEmpty()) {
            addAttributeValue(values, masterProfileId, sourceRecordId, src,
                    "interestedServices", String.join(",", data.getInterestedServices()), true, now);
        }

        if (!values.isEmpty()) {
            attributeValueRepository.saveAll(values);
        }
    }

    // Normalization helpers (same rules as ProfileMergeDecisionService)

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) return null;
        return email.trim().toLowerCase();
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("84") && digits.length() > 9) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }

    private void syncToUnomi(MasterProfile profile, ProfileSourceRecord sourceRecord, String syncType) {
        ProfileUnomiSyncLog syncLog = new ProfileUnomiSyncLog();
        syncLog.setMasterProfileId(profile.getId());
        syncLog.setProfileCode(profile.getProfileCode());
        syncLog.setSyncType(syncType);
        syncLog.setCreatedBy(SecurityUtils.getCurrentUsername().orElse("system"));

        try {
            Object result = unomiService.syncProfileToUnomi(profile).block();
            syncLog.setStatus((short) 1); // SUCCESS
            syncLog.setResponsePayload(result != null ? objectMapper.convertValue(result, Map.class) : null);
            syncLog.setSyncedAt(LocalDateTime.now());
            profile.setSyncedToUnomiAt(LocalDateTime.now());
            masterProfileRepository.save(profile);
            log.info("ProfileMergeExecutorService - Unomi sync SUCCESS: profileCode={}", profile.getProfileCode());
        } catch (Exception ex) {
            syncLog.setStatus((short) 2); // FAILED
            syncLog.setErrorMessage(ex.getMessage());
            syncLog.setSyncedAt(LocalDateTime.now());
            log.error("ProfileMergeExecutorService - Unomi sync FAILED: profileCode={}",
                    profile.getProfileCode(), ex);
        }
        unomiSyncLogRepository.save(syncLog);
    }

    // =====================================================================
    // CREATE PROFILE FOR REVIEW (used by CREATE_MATCH_CANDIDATE flow)
    // =====================================================================

    /**
     * Creates a new MasterProfile for an incoming medium/low-trust source when the decision is
     * CREATE_MATCH_CANDIDATE. Unlike {@link #createNewProfile}, this method:
     * - Does NOT mark the source record as MERGED (status stays as-is; caller sets NEED_REVIEW).
     * - Does NOT sync to Unomi.
     * - Only creates the profile, identity link, and attribute values so a match candidate can
     *   be built between the new profile and the existing CRM profile.
     */
    @Transactional
    public MasterProfile createProfileForReview(ProfileSourceRecord sourceRecord,
                                                NormalizedProfileData data) {
        log.info("ProfileMergeExecutorService - createProfileForReview: sourceSystem={}, sourceCustomerId={}",
                data.getSourceSystem(), data.getSourceCustomerId());

        // 1. Generate profileCode
        String profileCode = "MP_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        // 2. Save MasterProfile
        MasterProfile profile = new MasterProfile();
        profile.setProfileCode(profileCode);
        profile.setFullName(data.getFullName());
        profile.setPhone(data.getPhone());
        profile.setEmail(data.getEmail());
        profile.setIdentityNo(data.getIdentityNo());
        profile.setGender(data.getGender());
        profile.setDateOfBirth(data.getDateOfBirth());
        profile.setCustomerType(data.getCustomerType());
        profile.setProvinceCode(data.getProvinceCode());
        profile.setProvinceName(data.getProvinceName());
        profile.setUnitCode(data.getUnitCode());
        profile.setUnitName(data.getUnitName());
        profile.setStatus((short) 1); // ACTIVE
        profile.setSourceSummary(Map.of("initialSource", data.getSourceSystem(),
                "sourceCustomerId", data.getSourceCustomerId()));
        profile = masterProfileRepository.save(profile);
        log.info("ProfileMergeExecutorService - createProfileForReview: saved profile id={}, profileCode={}",
                profile.getId(), profile.getProfileCode());

        // 3. Create identity link (isPrimary=true, status=1)
        createIdentityLink(profile.getId(), data, true);

        // 4. Save attribute values (all selected=true — these are the profile's own values)
        saveAttributeValues(profile.getId(), sourceRecord.getId(), data, true);

        // 5. Link source record to the new profile (do NOT change mergeStatus here)
        sourceRecord.setMasterProfileId(profile.getId());
        sourceRecordRepository.save(sourceRecord);

        return profile;
    }

    private String buildIdentitySummary(NormalizedProfileData data) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(data.getIdentityNo())) sb.append("identityNo=").append(data.getIdentityNo()).append(";");
        if (StringUtils.hasText(data.getPhone())) sb.append("phone=").append(data.getPhone()).append(";");
        if (StringUtils.hasText(data.getEmail())) sb.append("email=").append(data.getEmail()).append(";");
        if (StringUtils.hasText(data.getSourceCustomerId())) sb.append("sourceId=").append(data.getSourceCustomerId());
        return sb.toString();
    }

}

