package vn.vnpost.cdp.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import vn.vnpost.cdp.common.utils.IdentityUtils;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.profile.entity.*;
import vn.vnpost.cdp.profile.enums.IdentityType;
import vn.vnpost.cdp.profile.event.ProfileMergedEvent;
import vn.vnpost.cdp.profile.repository.*;
import vn.vnpost.cdp.profile.service.ProfileMergeEngineService;
import vn.vnpost.cdp.security.SecurityUtils;

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
    private final ProfileMergeEngineService profileMergeEngineService;
    private final ApplicationEventPublisher eventPublisher;

    public ProfileMergeExecutorService(
            MasterProfileRepository masterProfileRepository,
            ProfileSourceRecordRepository sourceRecordRepository,
            ProfileIdentityLinkRepository identityLinkRepository,
            ProfileAttributeValueRepository attributeValueRepository,
            ProfileChangeLogRepository changeLogRepository,
            ProfileMergeConflictRepository conflictRepository,
            ProfileMergeEngineService profileMergeEngineService,
            ApplicationEventPublisher eventPublisher) {
        this.masterProfileRepository = masterProfileRepository;
        this.sourceRecordRepository = sourceRecordRepository;
        this.identityLinkRepository = identityLinkRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.changeLogRepository = changeLogRepository;
        this.conflictRepository = conflictRepository;
        this.profileMergeEngineService = profileMergeEngineService;
        this.eventPublisher = eventPublisher;
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
        profile.setTaxCode(data.getTaxCode());
        profile.setGender(data.getGender());
        profile.setDateOfBirth(data.getDateOfBirth());
        profile.setCustomerType(data.getCustomerType());
        profile.setCustomerTier(data.getCustomerTier());
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

        // 3. Create identity links (primary strong-identity + enrichment identifiers)
        createIdentityLink(profile.getId(), data, true);
        createEnrichmentIdentityLinks(profile.getId(), data);

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

        // 8. Publish event for async processing (Unomi Sync & Match Candidate Detection)
        eventPublisher.publishEvent(new ProfileMergedEvent(this, profile, "CREATE"));

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

        // 1. Ensure identity link exists — hỏi đúng câu "hồ sơ ĐÍCH đã có link ACTIVE cho source này
        // chưa", thay vì "có ai đó đã link chưa". Link status=3 (MERGED) sót lại từ lần merge trước
        // không được tính là đã link; và khi decide() chọn được đích trong nhiều candidate, link cũ
        // có thể đang trỏ sang candidate KHÁC nên vẫn phải tạo link mới cho đích.
        boolean linkedAlready = identityLinkRepository
                .findBySourceSystemAndSourceCustomerIdAndStatus(
                        data.getSourceSystem(), data.getSourceCustomerId(), (short) 1)
                .stream()
                .anyMatch(l -> l.getMasterProfileId().equals(targetProfile.getId()));
        if (!linkedAlready) {
            createIdentityLink(targetProfile.getId(), data, false);
        }
        // Enrichment identifiers (postId, appUserId, deviceId, ...) — luôn upsert, kể cả khi
        // source đã được link trước đó (event enrichment mới có thể mang thêm định danh).
        createEnrichmentIdentityLinks(targetProfile.getId(), data);

        // 2. Save incoming attribute values (not selected by default, selection handled below)
        saveAttributeValues(targetProfile.getId(), sourceRecord.getId(), data, false);


        // 3. Apply merge rules via rule engine (per-field, priority-based)
        boolean profileUpdated = false;

        profileUpdated |= applyFieldRule(targetProfile, sourceRecord, data,"phone",
                data.getPhone(), targetProfile.getPhone(), targetProfile::setPhone);
        profileUpdated |= applyFieldRule(targetProfile, sourceRecord, data, "email",
                data.getEmail(), targetProfile.getEmail(), targetProfile::setEmail);
        profileUpdated |= applyFieldRule(targetProfile, sourceRecord, data, "fullName",
                data.getFullName(), targetProfile.getFullName(), targetProfile::setFullName);
        profileUpdated |= applyFieldRule(targetProfile, sourceRecord, data, "identityNo",
                data.getIdentityNo(), targetProfile.getIdentityNo(), targetProfile::setIdentityNo);
        profileUpdated |= applyFieldRule(targetProfile, sourceRecord, data, "gender",
                data.getGender(), targetProfile.getGender(), targetProfile::setGender);
        profileUpdated |= applyFieldRule(targetProfile, sourceRecord, data, "dateOfBirth",
                data.getDateOfBirth(), targetProfile.getDateOfBirth(), targetProfile::setDateOfBirth);
        profileUpdated |= applyFieldRule(targetProfile, sourceRecord, data, "customerType",
                data.getCustomerType(), targetProfile.getCustomerType(), targetProfile::setCustomerType);
        profileUpdated |= applyFieldRule(targetProfile, sourceRecord, data, "customerTier",
                data.getCustomerTier(), targetProfile.getCustomerTier(), targetProfile::setCustomerTier);
        profileUpdated |= applyFieldRule(targetProfile, sourceRecord, data, "taxCode",
                data.getTaxCode(), targetProfile.getTaxCode(), targetProfile::setTaxCode);
        profileUpdated |= applyFieldRule(targetProfile, sourceRecord, data, "provinceCode",
                data.getProvinceCode(), targetProfile.getProvinceCode(),  value -> {
                    targetProfile.setProvinceCode(value);
                    if (StringUtils.hasText(data.getProvinceName())) {
                        targetProfile.setProvinceName(data.getProvinceName());
                    }
                }
        );
        profileUpdated |= applyFieldRule(targetProfile, sourceRecord, data, "unitCode",
                data.getUnitCode(), targetProfile.getUnitCode(),value -> {
                    targetProfile.setUnitCode(value);
                    if (StringUtils.hasText(data.getUnitName())) {
                        targetProfile.setUnitName(data.getUnitName());
                    }
                }
        );
       
        if (profileUpdated) {
            targetProfile.setLastMergedAt(LocalDateTime.now());
            masterProfileRepository.save(targetProfile);
        }

        // 4. Update source record
        sourceRecord.setMasterProfileId(targetProfile.getId());
        sourceRecord.setMergeStatus((short) 1); // MERGED
        sourceRecord.setProcessedAt(LocalDateTime.now());
        sourceRecordRepository.save(sourceRecord);

        // 5. Publish event for async processing (Unomi Sync & Match Candidate Detection)
        eventPublisher.publishEvent(new ProfileMergedEvent(this, targetProfile, "UPDATE"));

        return targetProfile;
    }


    private <T> boolean applyFieldRule(MasterProfile targetProfile,
                                       ProfileSourceRecord sourceRecord,
                                       NormalizedProfileData data,
                                       String propertyName,
                                       T incomingValue,
                                       T currentValue,
                                       java.util.function.Consumer<T> setter) {


        if (incomingValue == null) { return false;}
        if (incomingValue instanceof String str && !StringUtils.hasText(str)) {return false; }
        if (incomingValue.equals(currentValue)) { return false;}
        boolean overwrite = profileMergeEngineService.shouldOverwrite(
                targetProfile.getId(), propertyName, data.getSourceSystem(),sourceRecord.getReceivedAt());

        if (!overwrite) {
            log.info("ProfileMergeExecutorService - overwrite BLOCKED by rule engine: profileId={}, field={}, incomingSource={}",
                    targetProfile.getId(), propertyName, data.getSourceSystem());
            return false;
        }
        ProfileAttributeValue selected = attributeValueRepository
                .findFirstByMasterProfileIdAndPropertyNameAndIsSelectedTrue(
                        targetProfile.getId(), propertyName)
                .orElse(null);

        String oldSource = selected != null
                ? selected.getSourceSystem()
                : null;

        writeChangeLog(
                targetProfile.getId(),
                sourceRecord.getId(),
                data.getSourceSystem(),
                propertyName,
                currentValue != null ? currentValue.toString() : null,
                incomingValue.toString(),
                oldSource,
                data.getSourceSystem(),
                "RULE_ENGINE",
                "Rule engine allowed overwrite for " + propertyName
        );

        setter.accept(incomingValue);

        markAttributeSelected(targetProfile.getId(), propertyName, data.getSourceSystem());

        log.info("ProfileMergeExecutorService - field updated by rule engine: profileId={}, field={}, oldValue={}, newValue={}, newSource={}",
                targetProfile.getId(), propertyName, currentValue, incomingValue, data.getSourceSystem());

        return true;
    }


    private void markAttributeSelected(Long masterProfileId, String propertyName, String newSourceSystem) {
        List<ProfileAttributeValue> existing =
                attributeValueRepository.findByMasterProfileIdAndPropertyName(masterProfileId, propertyName);

        for (ProfileAttributeValue av : existing) {
            boolean shouldBeSelected = av.getSourceSystem() != null
                    && av.getSourceSystem().equalsIgnoreCase(newSourceSystem);
            if (!Boolean.valueOf(shouldBeSelected).equals(av.getIsSelected())) {
                av.setIsSelected(shouldBeSelected);
            }
        }
        attributeValueRepository.saveAll(existing);
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

        String incomingPhone  = IdentityUtils.normalizePhone(data.getPhone());
        String targetPhone    = IdentityUtils.normalizePhone(target.getPhone());
        String incomingEmail  = IdentityUtils.normalizeEmail(data.getEmail());
        String targetEmail    = IdentityUtils.normalizeEmail(target.getEmail());
        String incomingIdNo   = IdentityUtils.normalizeText(data.getIdentityNo());
        String targetIdNo     = IdentityUtils.normalizeText(target.getIdentityNo());

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
        IdentityType identityType;
        String identityValue;
        BigDecimal confidence;

        if (StringUtils.hasText(data.getIdentityNo())) {
            identityType = IdentityType.IDENTITY_NO;
            identityValue = data.getIdentityNo();
            confidence = BigDecimal.valueOf(100);
        } else if (StringUtils.hasText(data.getPhone())) {
            identityType = IdentityType.PHONE;
            identityValue = data.getPhone();
            confidence = BigDecimal.valueOf(90);
        } else if (StringUtils.hasText(data.getEmail())) {
            identityType = IdentityType.EMAIL;
            identityValue = data.getEmail();
            confidence = BigDecimal.valueOf(80);
        } else {
            identityType = IdentityType.SOURCE_CUSTOMER_ID;
            identityValue = data.getSourceCustomerId();
            confidence = BigDecimal.valueOf(60);
        }

        saveIdentityLinkIfAbsent(masterProfileId, data, identityType, identityValue, confidence, isPrimary);
    }

    /**
     * Tạo (upsert) các định danh liên nguồn từ event enrichment vào profile_identity_links.
     * Mỗi định danh được ghi với đúng identity_type chuẩn (danh mục {@link IdentityType}).
     * Không tạo trùng: nếu đã tồn tại link ACTIVE cùng (masterProfileId, identity_type, identity_value)
     * thì bỏ qua — nhờ đó nhiều event từ cùng nguồn không sinh bản ghi lặp.
     */
    private void createEnrichmentIdentityLinks(Long masterProfileId, NormalizedProfileData data) {
        saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.POST_ID,     data.getPostId(),     BigDecimal.valueOf(70), false);
        saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.CRM_ID,      data.getCrmId(),      BigDecimal.valueOf(85), false);
        saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.KHL_CODE,    data.getKhlCode(),    BigDecimal.valueOf(85), false);
        saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.APP_USER_ID, data.getAppUserId(),  BigDecimal.valueOf(70), false);
        saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.DEVICE_ID,   data.getDeviceId(),   BigDecimal.valueOf(50), false);
        saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.COOKIE_ID,   data.getCookieId(),   BigDecimal.valueOf(40), false);
        saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.PAYMENT_ID,  data.getPaymentId(),  BigDecimal.valueOf(60), false);
    }

    private void saveIdentityLinkIfAbsent(Long masterProfileId, NormalizedProfileData data,
                                          IdentityType identityType, String identityValue,
                                          BigDecimal confidence, boolean isPrimary) {
        if (!StringUtils.hasText(identityValue)) {
            return;
        }
        boolean exists = identityLinkRepository
                .findByMasterProfileIdAndIdentityTypeAndIdentityValue(
                        masterProfileId, identityType.name(), identityValue)
                .stream()
                .anyMatch(l -> l.getStatus() != null && l.getStatus() == 1);
        if (exists) {
            return;
        }

        ProfileIdentityLink link = new ProfileIdentityLink();
        link.setMasterProfileId(masterProfileId);
        link.setSourceSystem(data.getSourceSystem());
        link.setSourceCustomerId(data.getSourceCustomerId());
        link.setIdentityType(identityType.name());
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
                "taxCode", data.getTaxCode(), isSelected, now);
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
        addAttributeValue(values, masterProfileId, sourceRecordId, src, "taxCode",     data.getTaxCode(),     false, now);
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
        profile.setTaxCode(data.getTaxCode());
        profile.setGender(data.getGender());
        profile.setDateOfBirth(data.getDateOfBirth());
        profile.setCustomerType(data.getCustomerType());
        profile.setCustomerTier(data.getCustomerTier());
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

        // 3. Create identity links (primary + enrichment)
        createIdentityLink(profile.getId(), data, true);
        createEnrichmentIdentityLinks(profile.getId(), data);

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

