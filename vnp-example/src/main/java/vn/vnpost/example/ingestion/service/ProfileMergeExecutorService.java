package vn.vnpost.example.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.vnpost.example.ingestion.dto.NormalizedProfileData;
import vn.vnpost.example.profile.entity.*;
import vn.vnpost.example.profile.enums.IdentityType;
import vn.vnpost.example.profile.repository.*;
import vn.vnpost.example.profile.service.ProfileMergeEngineService;
import vn.vnpost.example.security.SecurityUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Reactive port của {@code ProfileMergeExecutorService} gốc. KHÔNG còn publish
 * {@code ProfileMergedEvent} ở đây — việc sync Unomi + phát hiện match-candidate sau merge được
 * {@code ProfileIngestionServiceImpl} gọi tường minh qua {@code ProfileMergePostProcessingService}
 * SAU KHI Mono ghi dữ liệu ở đây hoàn tất (tức sau khi transaction đã commit), thay cho
 * {@code @Async @TransactionalEventListener(AFTER_COMMIT)} không có tương đương reactive trực tiếp.
 */
@Slf4j
@Service
public class ProfileMergeExecutorService {

    private static final String CRM = "CRM";

    private final MasterProfileRepository masterProfileRepository;
    private final ProfileSourceRecordRepository sourceRecordRepository;
    private final ProfileIdentityLinkRepository identityLinkRepository;
    private final ProfileAttributeValueRepository attributeValueRepository;
    private final ProfileChangeLogRepository changeLogRepository;
    private final ProfileMergeConflictRepository conflictRepository;
    private final ProfileMergeEngineService profileMergeEngineService;

    public ProfileMergeExecutorService(
            MasterProfileRepository masterProfileRepository,
            ProfileSourceRecordRepository sourceRecordRepository,
            ProfileIdentityLinkRepository identityLinkRepository,
            ProfileAttributeValueRepository attributeValueRepository,
            ProfileChangeLogRepository changeLogRepository,
            ProfileMergeConflictRepository conflictRepository,
            ProfileMergeEngineService profileMergeEngineService) {
        this.masterProfileRepository = masterProfileRepository;
        this.sourceRecordRepository = sourceRecordRepository;
        this.identityLinkRepository = identityLinkRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.changeLogRepository = changeLogRepository;
        this.conflictRepository = conflictRepository;
        this.profileMergeEngineService = profileMergeEngineService;
    }

    // =====================================================================
    // CREATE NEW PROFILE
    // =====================================================================

    @Transactional
    public Mono<MasterProfile> createNewProfile(ProfileSourceRecord sourceRecord, NormalizedProfileData data) {
        log.info("ProfileMergeExecutorService - createNewProfile: sourceSystem={}, sourceCustomerId={}",
                data.getSourceSystem(), data.getSourceCustomerId());

        String profileCode = generateProfileCode();

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

        return masterProfileRepository.save(profile)
                .flatMap(saved -> {
                    log.info("ProfileMergeExecutorService - created MasterProfile id={}, profileCode={}",
                            saved.getId(), saved.getProfileCode());

                    return createIdentityLink(saved.getId(), data, true)
                            .then(createEnrichmentIdentityLinks(saved.getId(), data))
                            .then(saveAttributeValues(saved.getId(), sourceRecord.getId(), data, true))
                            .then(writeChangeLogs(saved.getId(), sourceRecord.getId(), data.getSourceSystem(), data,
                                    null, "AUTO_MERGE", "CREATE_NEW_PROFILE",
                                    "New master profile created from source data"))
                            .then(Mono.defer(() -> {
                                sourceRecord.setMasterProfileId(saved.getId());
                                sourceRecord.setMergeStatus((short) 1); // MERGED
                                sourceRecord.setProcessedAt(LocalDateTime.now());
                                return sourceRecordRepository.save(sourceRecord);
                            }))
                            .thenReturn(saved);
                });
    }

    // =====================================================================
    // AUTO MERGE
    // =====================================================================

    @Transactional
    public Mono<MasterProfile> autoMerge(ProfileSourceRecord sourceRecord, NormalizedProfileData data,
                                          MasterProfile targetProfile) {
        log.info("ProfileMergeExecutorService - autoMerge: sourceSystem={}, targetProfileId={}",
                data.getSourceSystem(), targetProfile.getId());

        // 1. Ensure identity link exists
        Mono<Void> ensureIdentityLink = identityLinkRepository
                .findBySourceSystemAndSourceCustomerId(data.getSourceSystem(), data.getSourceCustomerId())
                .hasElement()
                .flatMap(linkedAlready -> Boolean.TRUE.equals(linkedAlready)
                        ? Mono.<Void>empty()
                        : createIdentityLink(targetProfile.getId(), data, false));

        return ensureIdentityLink
                // Enrichment identifiers (postId, appUserId, deviceId, ...) — luôn upsert, kể cả khi
                // source đã được link trước đó (event enrichment mới có thể mang thêm định danh).
                .then(createEnrichmentIdentityLinks(targetProfile.getId(), data))
                // 2. Save incoming attribute values (not selected by default, selection handled below)
                .then(saveAttributeValues(targetProfile.getId(), sourceRecord.getId(), data, false))
                // 3. Apply merge rules via rule engine (per-field, priority-based)
                .then(applyAllFieldRules(targetProfile, sourceRecord, data))
                .flatMap(profileUpdated -> Boolean.TRUE.equals(profileUpdated)
                        ? Mono.defer(() -> {
                            targetProfile.setLastMergedAt(LocalDateTime.now());
                            return masterProfileRepository.save(targetProfile);
                        })
                        : Mono.just(targetProfile))
                // 4. Update source record
                .flatMap(saved -> Mono.defer(() -> {
                    sourceRecord.setMasterProfileId(saved.getId());
                    sourceRecord.setMergeStatus((short) 1); // MERGED
                    sourceRecord.setProcessedAt(LocalDateTime.now());
                    return sourceRecordRepository.save(sourceRecord).thenReturn(saved);
                }));
    }

    private Mono<Boolean> applyAllFieldRules(MasterProfile target, ProfileSourceRecord sourceRecord,
                                              NormalizedProfileData data) {
        List<Supplier<Mono<Boolean>>> rules = List.of(
                () -> applyFieldRule(target, sourceRecord, data, "phone",
                        data.getPhone(), target.getPhone(), target::setPhone),
                () -> applyFieldRule(target, sourceRecord, data, "email",
                        data.getEmail(), target.getEmail(), target::setEmail),
                () -> applyFieldRule(target, sourceRecord, data, "fullName",
                        data.getFullName(), target.getFullName(), target::setFullName),
                () -> applyFieldRule(target, sourceRecord, data, "identityNo",
                        data.getIdentityNo(), target.getIdentityNo(), target::setIdentityNo),
                () -> applyFieldRule(target, sourceRecord, data, "gender",
                        data.getGender(), target.getGender(), target::setGender),
                () -> applyFieldRule(target, sourceRecord, data, "dateOfBirth",
                        data.getDateOfBirth(), target.getDateOfBirth(), target::setDateOfBirth),
                () -> applyFieldRule(target, sourceRecord, data, "customerType",
                        data.getCustomerType(), target.getCustomerType(), target::setCustomerType),
                () -> applyFieldRule(target, sourceRecord, data, "customerTier",
                        data.getCustomerTier(), target.getCustomerTier(), target::setCustomerTier),
                () -> applyFieldRule(target, sourceRecord, data, "taxCode",
                        data.getTaxCode(), target.getTaxCode(), target::setTaxCode),
                () -> applyFieldRule(target, sourceRecord, data, "provinceCode",
                        data.getProvinceCode(), target.getProvinceCode(), (String value) -> {
                            target.setProvinceCode(value);
                            if (StringUtils.hasText(data.getProvinceName())) {
                                target.setProvinceName(data.getProvinceName());
                            }
                        }),
                () -> applyFieldRule(target, sourceRecord, data, "unitCode",
                        data.getUnitCode(), target.getUnitCode(), (String value) -> {
                            target.setUnitCode(value);
                            if (StringUtils.hasText(data.getUnitName())) {
                                target.setUnitName(data.getUnitName());
                            }
                        })
        );

        return Flux.fromIterable(rules)
                .concatMap(Supplier::get)
                .reduce(false, (a, b) -> a || b);
    }

    private <T> Mono<Boolean> applyFieldRule(MasterProfile targetProfile,
                                              ProfileSourceRecord sourceRecord,
                                              NormalizedProfileData data,
                                              String propertyName,
                                              T incomingValue,
                                              T currentValue,
                                              Consumer<T> setter) {

        if (incomingValue == null) {
            return Mono.just(false);
        }
        if (incomingValue instanceof String str && !StringUtils.hasText(str)) {
            return Mono.just(false);
        }
        if (incomingValue.equals(currentValue)) {
            return Mono.just(false);
        }

        return profileMergeEngineService.shouldOverwrite(
                        targetProfile.getId(), propertyName, data.getSourceSystem(), sourceRecord.getReceivedAt())
                .flatMap(overwrite -> {
                    if (!Boolean.TRUE.equals(overwrite)) {
                        log.info("ProfileMergeExecutorService - overwrite BLOCKED by rule engine: profileId={}, " +
                                        "field={}, incomingSource={}",
                                targetProfile.getId(), propertyName, data.getSourceSystem());
                        return Mono.just(false);
                    }

                    return attributeValueRepository
                            .findFirstByMasterProfileIdAndPropertyNameAndIsSelectedTrue(
                                    targetProfile.getId(), propertyName)
                            .map(Optional::of)
                            .defaultIfEmpty(Optional.empty())
                            .flatMap(selectedOpt -> {
                                String oldSource = selectedOpt.map(ProfileAttributeValue::getSourceSystem).orElse(null);

                                return writeChangeLog(
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
                                )
                                        .then(Mono.fromRunnable(() -> setter.accept(incomingValue)))
                                        .then(markAttributeSelected(targetProfile.getId(), propertyName,
                                                data.getSourceSystem()))
                                        .doOnSuccess(v -> log.info("ProfileMergeExecutorService - field updated by " +
                                                        "rule engine: profileId={}, field={}, oldValue={}, " +
                                                        "newValue={}, newSource={}",
                                                targetProfile.getId(), propertyName, currentValue, incomingValue,
                                                data.getSourceSystem()))
                                        .thenReturn(true);
                            });
                });
    }

    private Mono<Void> markAttributeSelected(Long masterProfileId, String propertyName, String newSourceSystem) {
        return attributeValueRepository.findByMasterProfileIdAndPropertyName(masterProfileId, propertyName)
                .collectList()
                .flatMap(existing -> {
                    for (ProfileAttributeValue av : existing) {
                        boolean shouldBeSelected = av.getSourceSystem() != null
                                && av.getSourceSystem().equalsIgnoreCase(newSourceSystem);
                        if (!Boolean.valueOf(shouldBeSelected).equals(av.getIsSelected())) {
                            av.setIsSelected(shouldBeSelected);
                        }
                    }
                    if (existing.isEmpty()) {
                        return Mono.empty();
                    }
                    return attributeValueRepository.saveAll(existing).then();
                });
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
    public Mono<Void> createMultipleCandidateConflict(ProfileSourceRecord sourceRecord,
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

        return conflictRepository.save(conflict)
                .then(SecurityUtils.getCurrentUsernameOrSystem().flatMap(actor -> {
                    ProfileChangeLog cl = new ProfileChangeLog();
                    cl.setMasterProfileId(first.getId());
                    cl.setSourceRecordId(sourceRecord.getId());
                    cl.setSourceSystem(data.getSourceSystem());
                    cl.setEventType("CONFLICT_DETECTED");
                    cl.setPropertyName("PROFILE_MATCH");
                    cl.setReason("Multiple candidates found: " + candidateIds);
                    cl.setChangedBy(actor);
                    cl.setChangedAt(LocalDateTime.now());
                    return changeLogRepository.save(cl);
                }))
                .then(Mono.defer(() -> {
                    sourceRecord.setMergeStatus((short) 2); // CONFLICT
                    sourceRecord.setProcessedAt(LocalDateTime.now());
                    return sourceRecordRepository.save(sourceRecord);
                }))
                .then();
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
    public Mono<Void> createFieldLevelNeedReview(ProfileSourceRecord sourceRecord,
                                                  NormalizedProfileData data,
                                                  MasterProfile target) {
        log.info("ProfileMergeExecutorService - createFieldLevelNeedReview: targetProfileId={}, sourceSystem={}",
                target.getId(), data.getSourceSystem());

        // 1. Link source record to the target profile
        sourceRecord.setMasterProfileId(target.getId());

        // 2. Save incoming attributes with appropriate selection flags
        return saveAttributeValuesForNeedReview(target.getId(), sourceRecord.getId(), data)
                .then(Mono.defer(() -> writeFieldConflicts(sourceRecord, data, target)))
                .then(Mono.defer(() -> {
                    // 4. Update source record
                    sourceRecord.setMergeStatus((short) 4); // NEED_REVIEW
                    sourceRecord.setProcessedAt(LocalDateTime.now());
                    return sourceRecordRepository.save(sourceRecord);
                }))
                .then();
    }

    private Mono<Void> writeFieldConflicts(ProfileSourceRecord sourceRecord, NormalizedProfileData data,
                                            MasterProfile target) {
        // 3. Field-level conflicts — only phone, email, identityNo (NOT fullName)
        List<String> conflictingFields = new ArrayList<>();
        List<Mono<Void>> conflictWrites = new ArrayList<>();

        String incomingPhone  = vn.vnpost.example.common.utils.IdentityUtils.normalizePhone(data.getPhone());
        String targetPhone    = vn.vnpost.example.common.utils.IdentityUtils.normalizePhone(target.getPhone());
        String incomingEmail  = vn.vnpost.example.common.utils.IdentityUtils.normalizeEmail(data.getEmail());
        String targetEmail    = vn.vnpost.example.common.utils.IdentityUtils.normalizeEmail(target.getEmail());
        String incomingIdNo   = vn.vnpost.example.common.utils.IdentityUtils.normalizeText(data.getIdentityNo());
        String targetIdNo     = vn.vnpost.example.common.utils.IdentityUtils.normalizeText(target.getIdentityNo());

        if (StringUtils.hasText(incomingPhone) && StringUtils.hasText(targetPhone)
                && !incomingPhone.equals(targetPhone)) {
            conflictingFields.add("phone");
            conflictWrites.add(createFieldConflict(sourceRecord, target, "phone",
                    target.getPhone(), data.getPhone(), data.getSourceSystem(),
                    "Incoming phone differs from selected phone"));
        }
        if (StringUtils.hasText(incomingEmail) && StringUtils.hasText(targetEmail)
                && !incomingEmail.equals(targetEmail)) {
            conflictingFields.add("email");
            conflictWrites.add(createFieldConflict(sourceRecord, target, "email",
                    target.getEmail(), data.getEmail(), data.getSourceSystem(),
                    "Incoming email differs from selected email"));
        }
        if (StringUtils.hasText(incomingIdNo) && StringUtils.hasText(targetIdNo)
                && !incomingIdNo.equals(targetIdNo)) {
            conflictingFields.add("identityNo");
            conflictWrites.add(createFieldConflict(sourceRecord, target, "identityNo",
                    target.getIdentityNo(), data.getIdentityNo(), data.getSourceSystem(),
                    "Incoming identityNo differs from selected identityNo"));
        }
        // fullName is intentionally excluded — name differences (accents, formatting) are
        // saved as attribute values only, not treated as hard conflicts.

        Mono<Void> writesChain = conflictWrites.isEmpty() ? Mono.empty() : Flux.concat(conflictWrites).then();

        if (conflictingFields.isEmpty()) {
            return writesChain;
        }

        Mono<Void> changeLogMono = SecurityUtils.getCurrentUsernameOrSystem().flatMap(actor -> {
            ProfileChangeLog cl = new ProfileChangeLog();
            cl.setMasterProfileId(target.getId());
            cl.setSourceRecordId(sourceRecord.getId());
            cl.setSourceSystem(data.getSourceSystem());
            cl.setEventType("CONFLICT_DETECTED");
            cl.setReason("Conflicting fields: " + String.join(", ", conflictingFields));
            cl.setChangedBy(actor);
            cl.setChangedAt(LocalDateTime.now());
            return changeLogRepository.save(cl);
        }).then();

        return writesChain.then(changeLogMono);
    }

    // =====================================================================
    // PRIVATE HELPERS
    // =====================================================================

    private String generateProfileCode() {
        return "MP_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private Mono<Void> createIdentityLink(Long masterProfileId, NormalizedProfileData data, boolean isPrimary) {
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

        return saveIdentityLinkIfAbsent(masterProfileId, data, identityType, identityValue, confidence, isPrimary);
    }

    /**
     * Tạo (upsert) các định danh liên nguồn từ event enrichment vào profile_identity_links.
     * Mỗi định danh được ghi với đúng identity_type chuẩn (danh mục {@link IdentityType}).
     * Không tạo trùng: nếu đã tồn tại link ACTIVE cùng (masterProfileId, identity_type, identity_value)
     * thì bỏ qua — nhờ đó nhiều event từ cùng nguồn không sinh bản ghi lặp.
     */
    private Mono<Void> createEnrichmentIdentityLinks(Long masterProfileId, NormalizedProfileData data) {
        return saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.POST_ID, data.getPostId(), BigDecimal.valueOf(70), false)
                .then(saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.CRM_ID, data.getCrmId(), BigDecimal.valueOf(85), false))
                .then(saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.KHL_CODE, data.getKhlCode(), BigDecimal.valueOf(85), false))
                .then(saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.APP_USER_ID, data.getAppUserId(), BigDecimal.valueOf(70), false))
                .then(saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.DEVICE_ID, data.getDeviceId(), BigDecimal.valueOf(50), false))
                .then(saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.COOKIE_ID, data.getCookieId(), BigDecimal.valueOf(40), false))
                .then(saveIdentityLinkIfAbsent(masterProfileId, data, IdentityType.PAYMENT_ID, data.getPaymentId(), BigDecimal.valueOf(60), false));
    }

    private Mono<Void> saveIdentityLinkIfAbsent(Long masterProfileId, NormalizedProfileData data,
                                                 IdentityType identityType, String identityValue,
                                                 BigDecimal confidence, boolean isPrimary) {
        if (!StringUtils.hasText(identityValue)) {
            return Mono.empty();
        }
        return identityLinkRepository
                .findByMasterProfileIdAndIdentityTypeAndIdentityValue(
                        masterProfileId, identityType.name(), identityValue)
                .any(l -> l.getStatus() != null && l.getStatus() == 1)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.<Void>empty();
                    }
                    return SecurityUtils.getCurrentUsernameOrSystem().flatMap(actor -> {
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
                        link.setLinkedBy(actor);
                        return identityLinkRepository.save(link);
                    }).then();
                });
    }

    private Mono<Void> saveAttributeValues(Long masterProfileId, Long sourceRecordId,
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

        if (values.isEmpty()) {
            return Mono.empty();
        }
        return attributeValueRepository.saveAll(values).then();
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

    private Mono<Void> writeChangeLogs(Long masterProfileId, Long sourceRecordId, String sourceSystem,
                                        NormalizedProfileData data, String oldSource,
                                        String eventType, String mergeStrategy, String reason) {
        return SecurityUtils.getCurrentUsernameOrSystem().flatMap(actor -> {
            List<ProfileChangeLog> logs = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

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

            if (logs.isEmpty()) {
                return Mono.<Void>empty();
            }
            return changeLogRepository.saveAll(logs).then();
        });
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

    private Mono<Void> writeChangeLog(Long masterProfileId, Long sourceRecordId, String sourceSystem,
                                       String propertyName, String oldValue, String newValue,
                                       String oldSource, String newSource, String mergeStrategy, String reason) {
        return SecurityUtils.getCurrentUsernameOrSystem().flatMap(actor -> {
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
            cl.setChangedBy(actor);
            cl.setChangedAt(LocalDateTime.now());
            return changeLogRepository.save(cl);
        }).then();
    }

    private Mono<Void> createFieldConflict(ProfileSourceRecord sourceRecord, MasterProfile target,
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
        return conflictRepository.save(conflict)
                .doOnNext(saved -> log.info("ProfileMergeExecutorService - conflict created: profileId={}, field={}",
                        target.getId(), propertyName))
                .then();
    }

    /**
     * Saves attribute values for the NEED_REVIEW flow.
     * Identity fields (phone, email, identityNo, fullName, gender, dateOfBirth, customerType)
     * are saved with isSelected=false so they do not overwrite the master profile's selected values.
     * Behavior/preference fields (interestedServices, lastVisitAt) are saved with isSelected=true
     * because CMS is the source of truth for these.
     */
    private Mono<Void> saveAttributeValuesForNeedReview(Long masterProfileId, Long sourceRecordId,
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

        if (values.isEmpty()) {
            return Mono.empty();
        }
        return attributeValueRepository.saveAll(values).then();
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
    public Mono<MasterProfile> createProfileForReview(ProfileSourceRecord sourceRecord,
                                                       NormalizedProfileData data) {
        log.info("ProfileMergeExecutorService - createProfileForReview: sourceSystem={}, sourceCustomerId={}",
                data.getSourceSystem(), data.getSourceCustomerId());

        String profileCode = generateProfileCode();

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

        return masterProfileRepository.save(profile)
                .flatMap(saved -> {
                    log.info("ProfileMergeExecutorService - createProfileForReview: saved profile id={}, profileCode={}",
                            saved.getId(), saved.getProfileCode());

                    return createIdentityLink(saved.getId(), data, true)
                            .then(createEnrichmentIdentityLinks(saved.getId(), data))
                            .then(saveAttributeValues(saved.getId(), sourceRecord.getId(), data, true))
                            .then(Mono.defer(() -> {
                                sourceRecord.setMasterProfileId(saved.getId());
                                return sourceRecordRepository.save(sourceRecord);
                            }))
                            .thenReturn(saved);
                });
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
