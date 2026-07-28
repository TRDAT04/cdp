package vn.vnpost.example.profile.service.match;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import vn.vnpost.example.common.exception.BusinessException;
import vn.vnpost.example.ingestion.dto.NormalizedProfileData;
import vn.vnpost.example.profile.dto.match.ProfileCandidateMergeRequest;
import vn.vnpost.example.profile.dto.match.ProfileMatchCandidateResponse;
import vn.vnpost.example.profile.dto.match.ProfileMatchCandidateSearchRequest;
import vn.vnpost.example.profile.dto.match.ProfileMatchReasonCreateItem;
import vn.vnpost.example.profile.dto.match.ProfileMatchReasonResponse;
import vn.vnpost.example.profile.dto.match.ProfileMatchScoreResult;
import vn.vnpost.example.profile.dto.match.ProfileMatchSideResponse;
import vn.vnpost.example.profile.entity.MasterProfile;
import vn.vnpost.example.profile.entity.ProfileAttributeValue;
import vn.vnpost.example.profile.entity.ProfileChangeLog;
import vn.vnpost.example.profile.entity.ProfileIdentityLink;
import vn.vnpost.example.profile.entity.ProfileMatchCandidate;
import vn.vnpost.example.profile.entity.ProfileMatchReason;
import vn.vnpost.example.profile.entity.ProfileMergeRequest;
import vn.vnpost.example.profile.entity.ProfileSourceRecord;
import vn.vnpost.example.profile.entity.ProfileUnomiSyncLog;
import vn.vnpost.example.profile.repository.MasterProfileRepository;
import vn.vnpost.example.profile.repository.ProfileAttributeValueRepository;
import vn.vnpost.example.profile.repository.ProfileChangeLogRepository;
import vn.vnpost.example.profile.repository.ProfileIdentityLinkRepository;
import vn.vnpost.example.profile.repository.ProfileMatchCandidateRepository;
import vn.vnpost.example.profile.repository.ProfileMatchReasonRepository;
import vn.vnpost.example.profile.repository.ProfileMergeRequestRepository;
import vn.vnpost.example.profile.repository.ProfileUnomiSyncLogRepository;
import vn.vnpost.example.security.SecurityUtils;
import vn.vnpost.example.unomi.service.UnomiService;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProfileMatchCandidateServiceImpl implements ProfileMatchCandidateService {

    private static final short STATUS_PENDING  = 0;
    private static final short STATUS_MERGED   = 1;
    private static final short STATUS_IGNORED  = 2;
    private static final short STATUS_REJECTED = 3;
    private static final short STATUS_EXPIRED  = 4;

    private static final short PROFILE_MERGED  = 3;
    private static final short PROFILE_DELETED = 5;

    private static final List<String> SOURCE_PRIORITY = List.of("CRM", "MYVNPOST", "PORTAL", "CMS");

    private static final String SEARCH_WHERE_SQL = """
            WHERE (:statusActive = false OR status = :status)
              AND (:matchLevelActive = false OR match_level = :matchLevel)
              AND (:minScoreActive = false OR match_score >= :minScore)
              AND (:sourceSystemActive = false OR left_source_system = :sourceSystem OR right_source_system = :sourceSystem)
              AND (:fromActive = false OR created >= :fromDate)
              AND (:toActive = false OR created <= :toDate)
            """;

    private final ProfileMatchCandidateRepository candidateRepository;
    private final ProfileMatchReasonRepository reasonRepository;
    private final MasterProfileRepository masterProfileRepository;
    private final ProfileIdentityLinkRepository identityLinkRepository;
    private final ProfileAttributeValueRepository attributeValueRepository;
    private final ProfileChangeLogRepository changeLogRepository;
    private final ProfileMergeRequestRepository mergeRequestRepository;
    private final ProfileUnomiSyncLogRepository unomiSyncLogRepository;
    private final ProfileMatchScoreService scoreService;
    private final UnomiService unomiService;
    private final ObjectMapper objectMapper;
    private final R2dbcEntityTemplate entityTemplate;

    public ProfileMatchCandidateServiceImpl(
            ProfileMatchCandidateRepository candidateRepository,
            ProfileMatchReasonRepository reasonRepository,
            MasterProfileRepository masterProfileRepository,
            ProfileIdentityLinkRepository identityLinkRepository,
            ProfileAttributeValueRepository attributeValueRepository,
            ProfileChangeLogRepository changeLogRepository,
            ProfileMergeRequestRepository mergeRequestRepository,
            ProfileUnomiSyncLogRepository unomiSyncLogRepository,
            ProfileMatchScoreService scoreService,
            UnomiService unomiService,
            ObjectMapper objectMapper,
            R2dbcEntityTemplate entityTemplate) {
        this.candidateRepository = candidateRepository;
        this.reasonRepository = reasonRepository;
        this.masterProfileRepository = masterProfileRepository;
        this.identityLinkRepository = identityLinkRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.changeLogRepository = changeLogRepository;
        this.mergeRequestRepository = mergeRequestRepository;
        this.unomiSyncLogRepository = unomiSyncLogRepository;
        this.scoreService = scoreService;
        this.unomiService = unomiService;
        this.objectMapper = objectMapper;
        this.entityTemplate = entityTemplate;
    }

    // =====================================================================
    // CREATE CANDIDATE (manual, admin — POST /profile-match-candidates)
    // =====================================================================

    @Override
    @Transactional
    public Mono<ProfileMatchCandidateResponse> createCandidate(Long leftId, Long rightId) {
        if (Objects.equals(leftId, rightId)) {
            return Mono.error(new BusinessException("INVALID_INPUT", "Left and right profiles must be different"));
        }

        return Mono.zip(loadActiveProfile(leftId), loadActiveProfile(rightId))
                .flatMap(t -> {
                    MasterProfile left = t.getT1();
                    MasterProfile right = t.getT2();

                    ProfileMatchScoreResult scoreResult = scoreService.calculate(left, right);
                    BigDecimal newScore = scoreResult.getScore();

                    if (newScore.compareTo(BigDecimal.valueOf(70)) < 0) {
                        return Mono.error(new BusinessException("SCORE_TOO_LOW",
                                "Match score is too low to create a candidate"));
                    }

                    return findExistingCandidate(leftId, rightId)
                            .flatMap(existing -> {
                                short existingStatus = existing.getStatus();

                                if (existingStatus == STATUS_PENDING || existingStatus == STATUS_MERGED) {
                                    return reasonRepository.findByMatchCandidateId(existing.getId())
                                            .collectList()
                                            .map(reasons -> toResponse(existing, left, right, reasons));
                                }

                                if (existingStatus == STATUS_IGNORED || existingStatus == STATUS_REJECTED) {
                                    BigDecimal diff = newScore.subtract(existing.getMatchScore());
                                    if (diff.compareTo(BigDecimal.TEN) < 0) {
                                        return Mono.error(new BusinessException("SCORE_NOT_IMPROVED",
                                                "New score must be at least 10 points higher than previous " +
                                                        "ignored/rejected candidate to recreate"));
                                    }
                                    existing.setStatus(STATUS_EXPIRED);
                                    return candidateRepository.save(existing)
                                            .then(createAndRespond(left, right, scoreResult));
                                }

                                return createAndRespond(left, right, scoreResult);
                            })
                            .switchIfEmpty(Mono.defer(() -> createAndRespond(left, right, scoreResult)));
                });
    }

    private Mono<ProfileMatchCandidateResponse> createAndRespond(MasterProfile left, MasterProfile right,
                                                                  ProfileMatchScoreResult scoreResult) {
        return persistCandidateWithReasons(left, right, scoreResult)
                .flatMap(saved -> reasonRepository.findByMatchCandidateId(saved.getId())
                        .collectList()
                        .map(reasons -> toResponse(saved, left, right, reasons)));
    }

    // =====================================================================
    // GET BY ID
    // =====================================================================

    @Override
    public Mono<ProfileMatchCandidateResponse> getById(Long id) {
        return toResponseWithLookup(loadCandidate(id));
    }

    private Mono<ProfileMatchCandidateResponse> toResponseWithLookup(Mono<ProfileMatchCandidate> candidateMono) {
        return candidateMono.flatMap(this::toResponseWithLookup);
    }

    private Mono<ProfileMatchCandidateResponse> toResponseWithLookup(ProfileMatchCandidate candidate) {
        return Mono.zip(
                optional(masterProfileRepository.findById(candidate.getLeftMasterProfileId())),
                optional(masterProfileRepository.findById(candidate.getRightMasterProfileId())),
                reasonRepository.findByMatchCandidateId(candidate.getId()).collectList()
        ).map(t -> toResponse(candidate, t.getT1().orElse(null), t.getT2().orElse(null), t.getT3()));
    }

    // =====================================================================
    // SEARCH
    // =====================================================================

    @Override
    public Mono<List<ProfileMatchCandidateResponse>> search(ProfileMatchCandidateSearchRequest req) {
        // NOTE: giữ nguyên hành vi bản gốc — req.getKeyword() KHÔNG được áp dụng làm điều kiện lọc
        // (đây là thiếu sót có sẵn ở bản JPA gốc: field tồn tại trong DTO/javadoc nhưng chưa từng
        // được dùng trong Specification). Không tự ý bổ sung logic lọc theo keyword.
        String sql = "SELECT * FROM profile_match_candidates " + SEARCH_WHERE_SQL
                + "ORDER BY match_score DESC, created DESC";

        return bindSearchParams(entityTemplate.getDatabaseClient().sql(sql), req)
                .map((row, metadata) -> entityTemplate.getConverter().read(ProfileMatchCandidate.class, row, metadata))
                .all()
                .collectList()
                .flatMap(this::toResponseListWithLookup);
    }

    private DatabaseClient.GenericExecuteSpec bindSearchParams(DatabaseClient.GenericExecuteSpec spec,
                                                                ProfileMatchCandidateSearchRequest req) {
        boolean statusActive = req.getStatus() != null;
        boolean matchLevelActive = StringUtils.hasText(req.getMatchLevel());
        boolean minScoreActive = req.getMinScore() != null;
        boolean sourceSystemActive = StringUtils.hasText(req.getSourceSystem());
        boolean fromActive = req.getFromDate() != null;
        boolean toActive = req.getToDate() != null;

        return spec
                .bind("statusActive", statusActive)
                .bind("status", statusActive ? req.getStatus() : (short) -1)
                .bind("matchLevelActive", matchLevelActive)
                .bind("matchLevel", matchLevelActive ? req.getMatchLevel() : "")
                .bind("minScoreActive", minScoreActive)
                .bind("minScore", minScoreActive ? req.getMinScore() : BigDecimal.ZERO)
                .bind("sourceSystemActive", sourceSystemActive)
                .bind("sourceSystem", sourceSystemActive ? req.getSourceSystem() : "")
                .bind("fromActive", fromActive)
                .bind("fromDate", fromActive ? req.getFromDate() : LocalDateTime.MIN)
                .bind("toActive", toActive)
                .bind("toDate", toActive ? req.getToDate() : LocalDateTime.MAX);
    }

    private Mono<List<ProfileMatchCandidateResponse>> toResponseListWithLookup(List<ProfileMatchCandidate> candidates) {
        return Flux.fromIterable(candidates)
                .concatMap(this::toResponseWithLookup)
                .collectList();
    }

    // =====================================================================
    // LIST
    // =====================================================================

    @Override
    public Mono<List<ProfileMatchCandidateResponse>> listPending() {
        return candidateRepository.findByStatusOrderByMatchScoreDescCreatedDesc(STATUS_PENDING)
                .collectList()
                .flatMap(this::toResponseListWithLookup);
    }

    @Override
    public Mono<List<ProfileMatchCandidateResponse>> listByStatus(Short status) {
        return candidateRepository.findByStatus(status)
                .collectList()
                .flatMap(this::toResponseListWithLookup);
    }

    @Override
    public Mono<List<ProfileMatchCandidateResponse>> listByProfile(Long masterProfileId) {
        return candidateRepository
                .findByLeftMasterProfileIdOrRightMasterProfileId(masterProfileId, masterProfileId)
                .collectList()
                .flatMap(this::toResponseListWithLookup);
    }

    // =====================================================================
    // IGNORE / REJECT
    // =====================================================================

    @Override
    @Transactional
    public Mono<ProfileMatchCandidateResponse> ignore(Long id) {
        return loadCandidate(id)
                .flatMap(this::validatePending)
                .flatMap(candidate -> SecurityUtils.getCurrentUsernameOrSystem().flatMap(actor -> {
                    candidate.setStatus(STATUS_IGNORED);
                    candidate.setDecisionBy(actor);
                    candidate.setDecisionAt(LocalDateTime.now());
                    return candidateRepository.save(candidate);
                }))
                .doOnNext(saved -> log.info("ProfileMatchCandidateServiceImpl - IGNORED candidate id={}", id))
                .then(getById(id));
    }

    @Override
    @Transactional
    public Mono<ProfileMatchCandidateResponse> reject(Long id) {
        return loadCandidate(id)
                .flatMap(this::validatePending)
                .flatMap(candidate -> SecurityUtils.getCurrentUsernameOrSystem().flatMap(actor -> {
                    candidate.setStatus(STATUS_REJECTED);
                    candidate.setDecisionBy(actor);
                    candidate.setDecisionAt(LocalDateTime.now());
                    return candidateRepository.save(candidate);
                }))
                .doOnNext(saved -> log.info("ProfileMatchCandidateServiceImpl - REJECTED candidate id={}", id))
                .then(getById(id));
    }

    // =====================================================================
    // MERGE
    // =====================================================================

    /**
     * Reactive port giữ nguyên đúng chuỗi ghi của bản gốc (JPA, class-level {@code @Transactional}):
     * tạo ProfileMergeRequest → update target (fill blanks) → copy identity links → copy attribute
     * values → update source (MERGED) → ghi change log → update candidate (MERGED) → sync Unomi.
     * {@code @Transactional} ở method này bọc TOÀN BỘ chuỗi (kể cả bước sync Unomi) trong CÙNG một
     * transaction — đúng như bản gốc (bản gốc gọi {@code syncToUnomi(...).block()} ngay trong cùng
     * method transactional, không tách async như luồng ingestion). Xem cảnh báo rủi ro ở báo cáo
     * cuối: giữ 1 network call (Unomi) bên trong transaction DB là rủi ro có sẵn từ bản gốc, không
     * phải lỗi tôi thêm vào — không tự ý tách ra vì bạn yêu cầu giữ nguyên business logic.
     */
    @Override
    @Transactional
    public Mono<ProfileMatchCandidateResponse> merge(Long id, ProfileCandidateMergeRequest request) {
        return loadCandidate(id)
                .flatMap(this::validatePending)
                .flatMap(candidate -> Mono.zip(loadProfile(candidate.getLeftMasterProfileId()),
                                loadProfile(candidate.getRightMasterProfileId()))
                        .flatMap(t -> {
                            MasterProfile left = t.getT1();
                            MasterProfile right = t.getT2();

                            MasterProfile target;
                            MasterProfile source;
                            if (request.getTargetMasterProfileId() != null) {
                                if (!request.getTargetMasterProfileId().equals(left.getId())
                                        && !request.getTargetMasterProfileId().equals(right.getId())) {
                                    return Mono.<ProfileMatchCandidateResponse>error(new BusinessException("INVALID_TARGET",
                                            "targetMasterProfileId must be either leftMasterProfileId or rightMasterProfileId"));
                                }
                                target = request.getTargetMasterProfileId().equals(left.getId()) ? left : right;
                                source = (target == left) ? right : left;
                            } else {
                                target = chooseTarget(left, right, candidate);
                                source = (target == left) ? right : left;
                            }

                            return doMerge(id, candidate, target, source, request);
                        }));
    }

    private Mono<ProfileMatchCandidateResponse> doMerge(Long candidateId, ProfileMatchCandidate candidate,
                                                         MasterProfile target, MasterProfile source,
                                                         ProfileCandidateMergeRequest request) {
        return SecurityUtils.getCurrentUsernameOrSystem().flatMap(actor -> {
            LocalDateTime now = LocalDateTime.now();

            // 5. Create merge request
            ProfileMergeRequest mergeReq = new ProfileMergeRequest();
            mergeReq.setSourceMasterProfileId(source.getId());
            mergeReq.setTargetMasterProfileId(target.getId());
            mergeReq.setMergeReason(request.getMergeReason());
            mergeReq.setStatus((short) 3); // COMPLETED
            mergeReq.setRequestedBy(actor);
            mergeReq.setApprovedBy(actor);
            mergeReq.setRequestedAt(now);
            mergeReq.setApprovedAt(now);
            mergeReq.setCompletedAt(now);

            return mergeRequestRepository.save(mergeReq)
                    .flatMap(savedMergeReq -> {
                        // 6. Merge data (fill blanks only)
                        mergeProfileData(target, source);
                        target.setLastMergedAt(now);

                        return masterProfileRepository.save(target)
                                // 8. Copy identity links
                                .then(copyIdentityLinks(source.getId(), target.getId(), actor, now))
                                // 9. Copy attribute values
                                .then(copyAttributeValues(source.getId(), target.getId()))
                                // 10. Update source profile
                                .then(Mono.defer(() -> {
                                    source.setStatus(PROFILE_MERGED);
                                    source.setMergedIntoProfileId(target.getId());
                                    return masterProfileRepository.save(source);
                                }))
                                // 11. Write change log
                                .then(Mono.defer(() -> {
                                    boolean leftIsSource = candidate.getLeftMasterProfileId().equals(source.getId());
                                    boolean leftIsTarget = candidate.getLeftMasterProfileId().equals(target.getId());

                                    ProfileChangeLog cl = new ProfileChangeLog();
                                    cl.setMasterProfileId(target.getId());
                                    cl.setSourceSystem(leftIsSource ? candidate.getLeftSourceSystem() : candidate.getRightSourceSystem());
                                    cl.setEventType("ADMIN_MERGE");
                                    cl.setPropertyName("PROFILE_MERGE");
                                    cl.setOldValue(source.getProfileCode());
                                    cl.setNewValue(target.getProfileCode());
                                    cl.setSelectedValue(target.getProfileCode());
                                    cl.setOldSource(leftIsSource ? candidate.getLeftSourceSystem() : candidate.getRightSourceSystem());
                                    cl.setNewSource(leftIsTarget ? candidate.getLeftSourceSystem() : candidate.getRightSourceSystem());
                                    cl.setMergeStrategy("ADMIN_DECISION");
                                    cl.setReason(request.getMergeReason());
                                    cl.setChangedBy(actor);
                                    cl.setChangedAt(now);
                                    return changeLogRepository.save(cl);
                                }))
                                // 12. Update candidate
                                .then(Mono.defer(() -> {
                                    candidate.setStatus(STATUS_MERGED);
                                    candidate.setDecisionBy(actor);
                                    candidate.setDecisionAt(now);
                                    candidate.setMergeRequestId(savedMergeReq.getId());
                                    return candidateRepository.save(candidate);
                                }))
                                // 13. Sync target to Unomi (INLINE, cùng transaction — giống bản gốc)
                                .then(syncToUnomi(target, "MERGE"))
                                .then(getById(candidateId))
                                .doOnNext(r -> log.info("ProfileMatchCandidateServiceImpl - MERGED candidate id={}, " +
                                                "source={}, target={}", candidateId, source.getId(), target.getId()));
                    });
        });
    }

    private Mono<Void> copyIdentityLinks(Long sourceId, Long targetId, String actor, LocalDateTime now) {
        return Mono.zip(
                identityLinkRepository.findByMasterProfileId(sourceId).collectList(),
                identityLinkRepository.findByMasterProfileId(targetId).collectList()
        ).flatMap(t -> {
            List<ProfileIdentityLink> sourceLinks = t.getT1();
            List<ProfileIdentityLink> targetLinks = t.getT2();

            return Flux.fromIterable(sourceLinks)
                    .concatMap(sl -> {
                        boolean exists = targetLinks.stream().anyMatch(tl ->
                                Objects.equals(tl.getSourceSystem(), sl.getSourceSystem())
                                        && Objects.equals(tl.getSourceCustomerId(), sl.getSourceCustomerId()));

                        Mono<Void> createIfAbsent = exists ? Mono.empty() : Mono.defer(() -> {
                            ProfileIdentityLink newLink = new ProfileIdentityLink();
                            newLink.setMasterProfileId(targetId);
                            newLink.setSourceSystem(sl.getSourceSystem());
                            newLink.setSourceCustomerId(sl.getSourceCustomerId());
                            newLink.setIdentityType(sl.getIdentityType());
                            newLink.setIdentityValue(sl.getIdentityValue());
                            newLink.setConfidenceScore(sl.getConfidenceScore());
                            newLink.setIsPrimary(false);
                            newLink.setStatus((short) 1);
                            newLink.setLinkedBy(actor);
                            newLink.setLinkedAt(now);
                            return identityLinkRepository.save(newLink).then();
                        });

                        sl.setStatus((short) 3); // MERGED
                        return createIfAbsent.then(identityLinkRepository.save(sl));
                    })
                    .then();
        });
    }

    private Mono<Void> copyAttributeValues(Long sourceId, Long targetId) {
        return Mono.zip(
                attributeValueRepository.findByMasterProfileId(sourceId).collectList(),
                attributeValueRepository.findByMasterProfileId(targetId).collectList()
        ).flatMap(t -> {
            List<ProfileAttributeValue> sourceValues = t.getT1();
            List<ProfileAttributeValue> targetValues = t.getT2();

            return Flux.fromIterable(sourceValues)
                    .concatMap(sv -> {
                        boolean exists = targetValues.stream().anyMatch(tv ->
                                Objects.equals(tv.getSourceSystem(), sv.getSourceSystem())
                                        && Objects.equals(tv.getPropertyName(), sv.getPropertyName())
                                        && Objects.equals(tv.getPropertyValue(), sv.getPropertyValue()));
                        if (exists) {
                            return Mono.<Void>empty();
                        }
                        boolean noSelectedInTarget = targetValues.stream().noneMatch(tv ->
                                Objects.equals(tv.getPropertyName(), sv.getPropertyName())
                                        && Boolean.TRUE.equals(tv.getIsSelected()));
                        ProfileAttributeValue nv = new ProfileAttributeValue();
                        nv.setMasterProfileId(targetId);
                        nv.setSourceRecordId(sv.getSourceRecordId());
                        nv.setSourceSystem(sv.getSourceSystem());
                        nv.setPropertyName(sv.getPropertyName());
                        nv.setPropertyValue(sv.getPropertyValue());
                        nv.setNormalizedValue(sv.getNormalizedValue());
                        nv.setConfidenceScore(sv.getConfidenceScore());
                        nv.setIsSelected(noSelectedInTarget);
                        nv.setReceivedAt(sv.getReceivedAt());
                        return attributeValueRepository.save(nv).then();
                    })
                    .then();
        });
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> syncToUnomi(MasterProfile profile, String syncType) {
        return SecurityUtils.getCurrentUsernameOrSystem().flatMap(actor -> {
            ProfileUnomiSyncLog syncLog = new ProfileUnomiSyncLog();
            syncLog.setMasterProfileId(profile.getId());
            syncLog.setProfileCode(profile.getProfileCode());
            syncLog.setSyncType(syncType);
            syncLog.setCreatedBy(actor);

            return unomiService.syncProfileToUnomi(profile)
                    .flatMap(result -> {
                        syncLog.setStatus((short) 1);
                        syncLog.setResponsePayload(result != null
                                ? objectMapper.convertValue(result, Map.class) : null);
                        syncLog.setSyncedAt(LocalDateTime.now());
                        profile.setSyncedToUnomiAt(LocalDateTime.now());
                        return masterProfileRepository.save(profile);
                    })
                    .onErrorResume(ex -> {
                        syncLog.setStatus((short) 2);
                        syncLog.setErrorMessage(ex.getMessage());
                        syncLog.setSyncedAt(LocalDateTime.now());
                        log.error("ProfileMatchCandidateServiceImpl - Unomi sync FAILED: profileCode={}",
                                profile.getProfileCode(), ex);
                        return Mono.empty();
                    })
                    .then(Mono.defer(() -> unomiSyncLogRepository.save(syncLog)))
                    .then();
        });
    }

    // =====================================================================
    // CREATE CANDIDATE BETWEEN TWO KNOWN PROFILES (CREATE_MATCH_CANDIDATE flow)
    // =====================================================================

    @Override
    public Mono<ProfileMatchCandidate> createCandidateBetweenProfiles(Long existingProfileId,
                                                                       Long newProfileId,
                                                                       NormalizedProfileData incomingData,
                                                                       ProfileSourceRecord sourceRecord) {
        log.info("ProfileMatchCandidateServiceImpl - createCandidateBetweenProfiles: existing={}, new={}",
                existingProfileId, newProfileId);

        if (Objects.equals(existingProfileId, newProfileId)) {
            return Mono.error(new BusinessException("INVALID_INPUT",
                    "existingProfileId and newProfileId must be different"));
        }

        Mono<MasterProfile> existingMono = masterProfileRepository.findById(existingProfileId)
                .switchIfEmpty(Mono.error(new BusinessException("NOT_FOUND",
                        "Existing master profile not found: " + existingProfileId)));
        Mono<MasterProfile> newProfileMono = masterProfileRepository.findById(newProfileId)
                .switchIfEmpty(Mono.error(new BusinessException("NOT_FOUND",
                        "New master profile not found: " + newProfileId)));

        return Mono.zip(existingMono, newProfileMono).flatMap(t -> {
            MasterProfile existing = t.getT1();
            MasterProfile newProfile = t.getT2();

            if (isMergedOrDeleted(existing.getStatus())) {
                return Mono.error(new BusinessException("INVALID_PROFILE",
                        "Existing profile " + existingProfileId + " is MERGED or DELETED"));
            }
            if (isMergedOrDeleted(newProfile.getStatus())) {
                return Mono.error(new BusinessException("INVALID_PROFILE",
                        "New profile " + newProfileId + " is MERGED or DELETED"));
            }

            return candidateRepository.existsPendingOrMergedBetween(existingProfileId, newProfileId)
                    .flatMap(exists -> {
                        if (Boolean.TRUE.equals(exists)) {
                            return candidateRepository.findBetween(existingProfileId, newProfileId)
                                    .next()
                                    .doOnNext(c -> log.info("ProfileMatchCandidateServiceImpl - candidate already " +
                                                    "exists between ({},{}), returning existing",
                                            existingProfileId, newProfileId))
                                    .switchIfEmpty(Mono.defer(() ->
                                            buildAndPersistCandidateFromIncoming(existing, newProfile, incomingData)));
                        }
                        return buildAndPersistCandidateFromIncoming(existing, newProfile, incomingData);
                    });
        });
    }

    private Mono<ProfileMatchCandidate> buildAndPersistCandidateFromIncoming(MasterProfile existing,
                                                                              MasterProfile newProfile,
                                                                              NormalizedProfileData incomingData) {
        ProfileMatchScoreResult scoreResult = scoreService.calculate(existing, newProfile);

        return Mono.zip(optional(getPrimaryLink(existing.getId())), optional(getPrimaryLink(newProfile.getId())))
                .flatMap(links -> {
                    ProfileIdentityLink existingLink = links.getT1().orElse(null);
                    ProfileIdentityLink newLink = links.getT2().orElse(null);

                    ProfileMatchCandidate candidate = new ProfileMatchCandidate();
                    candidate.setLeftMasterProfileId(existing.getId());
                    candidate.setRightMasterProfileId(newProfile.getId());
                    candidate.setLeftSourceSystem(existingLink != null ? existingLink.getSourceSystem() : null);
                    candidate.setLeftSourceCustomerId(existingLink != null ? existingLink.getSourceCustomerId() : null);
                    // Use incoming data for right side — it's freshest
                    candidate.setRightSourceSystem(incomingData.getSourceSystem());
                    candidate.setRightSourceCustomerId(incomingData.getSourceCustomerId());
                    candidate.setLeftSnapshot(buildSnapshot(existing, existingLink));
                    candidate.setRightSnapshot(buildSnapshot(newProfile, newLink));
                    candidate.setMatchScore(scoreResult.getScore());
                    candidate.setMatchLevel(scoreResult.getMatchLevel());
                    candidate.setStatus(STATUS_PENDING);

                    return candidateRepository.save(candidate)
                            .flatMap(saved -> saveReasons(saved.getId(), scoreResult.getReasons())
                                    .thenReturn(saved))
                            .doOnNext(saved -> log.info("ProfileMatchCandidateServiceImpl - created candidate " +
                                            "id={} between ({},{}), score={}",
                                    saved.getId(), existing.getId(), newProfile.getId(), scoreResult.getScore()));
                });
    }

    // =====================================================================
    // DETECT AND CREATE CANDIDATES
    // =====================================================================

    @Override
    public Mono<Void> detectAndCreateCandidatesForProfile(Long masterProfileId) {
        return masterProfileRepository.findById(masterProfileId)
                .flatMap(profile -> {
                    if (isMergedOrDeleted(profile.getStatus())) {
                        log.debug("ProfileMatchCandidateServiceImpl - detectAndCreate: skip merged/deleted profile {}",
                                masterProfileId);
                        return Mono.<Void>empty();
                    }
                    return buildCandidatePoolIds(profile)
                            .flatMap(candidateIds -> {
                                log.info("ProfileMatchCandidateServiceImpl - detectAndCreate: profile={}, " +
                                        "candidate pool size={}", masterProfileId, candidateIds.size());
                                return Flux.fromIterable(candidateIds)
                                        .concatMap(candidateId -> processOneCandidate(profile, candidateId))
                                        .then();
                            });
                })
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn(
                        "ProfileMatchCandidateServiceImpl - detectAndCreate: profile {} not found", masterProfileId)))
                .then();
    }

    private Mono<Set<Long>> buildCandidatePoolIds(MasterProfile profile) {
        Mono<Optional<Long>> byIdentityNo = StringUtils.hasText(profile.getIdentityNo())
                ? optional(masterProfileRepository.findByIdentityNo(profile.getIdentityNo())
                        .map(MasterProfile::getId)
                        .filter(id -> !id.equals(profile.getId())))
                : Mono.just(Optional.empty());
        Mono<Optional<Long>> byPhone = StringUtils.hasText(profile.getPhone())
                ? optional(masterProfileRepository.findByPhone(profile.getPhone())
                        .map(MasterProfile::getId)
                        .filter(id -> !id.equals(profile.getId())))
                : Mono.just(Optional.empty());
        Mono<Optional<Long>> byEmail = StringUtils.hasText(profile.getEmail())
                ? optional(masterProfileRepository.findByEmail(profile.getEmail())
                        .map(MasterProfile::getId)
                        .filter(id -> !id.equals(profile.getId())))
                : Mono.just(Optional.empty());

        return Mono.zip(byIdentityNo, byPhone, byEmail)
                .map(t -> {
                    Set<Long> ids = new LinkedHashSet<>();
                    t.getT1().ifPresent(ids::add);
                    t.getT2().ifPresent(ids::add);
                    t.getT3().ifPresent(ids::add);
                    return ids;
                });
    }

    private Mono<Void> processOneCandidate(MasterProfile profile, Long candidateProfileId) {
        return masterProfileRepository.findById(candidateProfileId)
                .flatMap(candidateProfile -> {
                    if (isMergedOrDeleted(candidateProfile.getStatus())) {
                        return Mono.<Void>empty();
                    }

                    ProfileMatchScoreResult scoreResult = scoreService.calculate(profile, candidateProfile);
                    if (scoreResult.getScore().compareTo(BigDecimal.valueOf(70)) < 0) {
                        log.debug("ProfileMatchCandidateServiceImpl - score {} too low for pair ({},{})",
                                scoreResult.getScore(), profile.getId(), candidateProfileId);
                        return Mono.<Void>empty();
                    }

                    Mono<Boolean> hasPendingMono = Mono.zip(
                            candidateRepository.existsByLeftMasterProfileIdAndRightMasterProfileIdAndStatus(
                                    profile.getId(), candidateProfileId, STATUS_PENDING),
                            candidateRepository.existsByRightMasterProfileIdAndLeftMasterProfileIdAndStatus(
                                    profile.getId(), candidateProfileId, STATUS_PENDING)
                    ).map(t -> Boolean.TRUE.equals(t.getT1()) || Boolean.TRUE.equals(t.getT2()));
                    Mono<Boolean> hasMergedMono = Mono.zip(
                            candidateRepository.existsByLeftMasterProfileIdAndRightMasterProfileIdAndStatus(
                                    profile.getId(), candidateProfileId, STATUS_MERGED),
                            candidateRepository.existsByRightMasterProfileIdAndLeftMasterProfileIdAndStatus(
                                    profile.getId(), candidateProfileId, STATUS_MERGED)
                    ).map(t -> Boolean.TRUE.equals(t.getT1()) || Boolean.TRUE.equals(t.getT2()));

                    return Mono.zip(hasPendingMono, hasMergedMono)
                            .flatMap(t -> {
                                if (t.getT1() || t.getT2()) {
                                    log.debug("ProfileMatchCandidateServiceImpl - already has pending/merged " +
                                            "candidate for pair ({},{})", profile.getId(), candidateProfileId);
                                    return Mono.<Void>empty();
                                }
                                return continueOrCreate(profile, candidateProfile, scoreResult);
                            });
                })
                .onErrorResume(ex -> {
                    log.error("ProfileMatchCandidateServiceImpl - error for pair ({},{}): {}",
                            profile.getId(), candidateProfileId, ex.getMessage(), ex);
                    return Mono.empty();
                });
    }

    /**
     * Kiểm tra candidate IGNORED/REJECTED cũ (chỉ tạo lại nếu score cao hơn >= 10 điểm), rồi
     * luôn gọi persistCandidateWithReasons — kể cả khi có candidate cũ ở trạng thái khác (EXPIRED...),
     * đúng theo fallthrough của bản gốc.
     */
    private Mono<Void> continueOrCreate(MasterProfile profile, MasterProfile candidateProfile,
                                         ProfileMatchScoreResult scoreResult) {
        return findExistingCandidate(profile.getId(), candidateProfile.getId())
                .flatMap(existing -> {
                    if (existing.getStatus() == STATUS_IGNORED || existing.getStatus() == STATUS_REJECTED) {
                        BigDecimal diff = scoreResult.getScore().subtract(existing.getMatchScore());
                        if (diff.compareTo(BigDecimal.TEN) < 0) {
                            return Mono.just(false);
                        }
                        existing.setStatus(STATUS_EXPIRED);
                        return candidateRepository.save(existing).thenReturn(true);
                    }
                    return Mono.just(true);
                })
                .defaultIfEmpty(true)
                .flatMap(proceed -> {
                    if (!Boolean.TRUE.equals(proceed)) {
                        return Mono.empty();
                    }
                    return persistCandidateWithReasons(profile, candidateProfile, scoreResult)
                            .doOnNext(saved -> log.info(
                                    "ProfileMatchCandidateServiceImpl - created candidate for pair ({},{})",
                                    profile.getId(), candidateProfile.getId()));
                })
                .then();
    }

    private Mono<ProfileMatchCandidate> persistCandidateWithReasons(MasterProfile left, MasterProfile right,
                                                                     ProfileMatchScoreResult scoreResult) {
        return Mono.zip(optional(getPrimaryLink(left.getId())), optional(getPrimaryLink(right.getId())))
                .flatMap(links -> {
                    ProfileIdentityLink leftLink = links.getT1().orElse(null);
                    ProfileIdentityLink rightLink = links.getT2().orElse(null);

                    ProfileMatchCandidate candidate = new ProfileMatchCandidate();
                    candidate.setLeftMasterProfileId(left.getId());
                    candidate.setRightMasterProfileId(right.getId());
                    candidate.setLeftSourceSystem(leftLink != null ? leftLink.getSourceSystem() : null);
                    candidate.setLeftSourceCustomerId(leftLink != null ? leftLink.getSourceCustomerId() : null);
                    candidate.setRightSourceSystem(rightLink != null ? rightLink.getSourceSystem() : null);
                    candidate.setRightSourceCustomerId(rightLink != null ? rightLink.getSourceCustomerId() : null);
                    candidate.setLeftSnapshot(buildSnapshot(left, leftLink));
                    candidate.setRightSnapshot(buildSnapshot(right, rightLink));
                    candidate.setMatchScore(scoreResult.getScore());
                    candidate.setMatchLevel(scoreResult.getMatchLevel());
                    candidate.setStatus(STATUS_PENDING);

                    return candidateRepository.save(candidate)
                            .flatMap(saved -> saveReasons(saved.getId(), scoreResult.getReasons()).thenReturn(saved));
                });
    }

    private Mono<Void> saveReasons(Long candidateId, List<ProfileMatchReasonCreateItem> items) {
        List<ProfileMatchReason> reasons = items.stream().map(item -> {
            ProfileMatchReason r = new ProfileMatchReason();
            r.setMatchCandidateId(candidateId);
            r.setReasonType(item.getReasonType());
            r.setReasonMessage(item.getReasonMessage());
            r.setLeftValue(item.getLeftValue());
            r.setRightValue(item.getRightValue());
            r.setScore(item.getScore());
            return r;
        }).collect(Collectors.toList());
        return reasonRepository.saveAll(reasons).then();
    }

    private Mono<ProfileMatchCandidate> findExistingCandidate(Long leftId, Long rightId) {
        return candidateRepository.findTopByLeftMasterProfileIdAndRightMasterProfileIdOrderByCreatedDesc(leftId, rightId)
                .switchIfEmpty(candidateRepository
                        .findTopByRightMasterProfileIdAndLeftMasterProfileIdOrderByCreatedDesc(leftId, rightId));
    }

    private Mono<ProfileIdentityLink> getPrimaryLink(Long masterProfileId) {
        return identityLinkRepository.findByMasterProfileId(masterProfileId)
                .collectList()
                .flatMap(links -> {
                    Optional<ProfileIdentityLink> primary = links.stream()
                            .filter(l -> Boolean.TRUE.equals(l.getIsPrimary()) && l.getStatus() == 1)
                            .findFirst();
                    if (primary.isPresent()) {
                        return Mono.just(primary.get());
                    }
                    Optional<ProfileIdentityLink> active = links.stream()
                            .filter(l -> l.getStatus() == 1)
                            .findFirst();
                    if (active.isPresent()) {
                        return Mono.just(active.get());
                    }
                    if (!links.isEmpty()) {
                        return Mono.just(links.get(0));
                    }
                    return Mono.empty();
                });
    }

    private Map<String, Object> buildSnapshot(MasterProfile p, ProfileIdentityLink link) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("masterProfileId",  p.getId());
        m.put("profileCode",      p.getProfileCode());
        m.put("fullName",         p.getFullName());
        m.put("phone",            p.getPhone());
        m.put("email",            p.getEmail());
        m.put("identityNo",       p.getIdentityNo());
        m.put("customerType",     p.getCustomerType());
        m.put("provinceCode",     p.getProvinceCode());
        m.put("provinceName",     p.getProvinceName());
        m.put("unitCode",         p.getUnitCode());
        m.put("unitName",         p.getUnitName());
        m.put("sourceSystem",     link != null ? link.getSourceSystem() : null);
        m.put("sourceCustomerId", link != null ? link.getSourceCustomerId() : null);
        return m;
    }

    // =====================================================================
    // HELPERS chung cho merge()
    // =====================================================================

    private MasterProfile chooseTarget(MasterProfile left, MasterProfile right, ProfileMatchCandidate candidate) {
        int leftPri  = getSourcePriority(candidate.getLeftSourceSystem());
        int rightPri = getSourcePriority(candidate.getRightSourceSystem());

        if (leftPri < rightPri) return left;
        if (rightPri < leftPri) return right;

        int leftFields  = countIdentityFields(left);
        int rightFields = countIdentityFields(right);
        if (leftFields > rightFields) return left;
        if (rightFields > leftFields) return right;

        if (left.getCreated() != null && right.getCreated() != null) {
            return left.getCreated().isBefore(right.getCreated()) ? left : right;
        }
        return left;
    }

    private int getSourcePriority(String source) {
        if (source == null) return Integer.MAX_VALUE;
        int idx = SOURCE_PRIORITY.indexOf(source.toUpperCase());
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }

    private int countIdentityFields(MasterProfile p) {
        int c = 0;
        if (StringUtils.hasText(p.getIdentityNo())) c++;
        if (StringUtils.hasText(p.getPhone()))       c++;
        if (StringUtils.hasText(p.getEmail()))       c++;
        if (StringUtils.hasText(p.getFullName()))    c++;
        if (p.getDateOfBirth() != null)              c++;
        return c;
    }

    private void mergeProfileData(MasterProfile target, MasterProfile source) {
        if (!StringUtils.hasText(target.getFullName())    && StringUtils.hasText(source.getFullName()))
            target.setFullName(source.getFullName());
        if (!StringUtils.hasText(target.getPhone())       && StringUtils.hasText(source.getPhone()))
            target.setPhone(source.getPhone());
        if (!StringUtils.hasText(target.getEmail())       && StringUtils.hasText(source.getEmail()))
            target.setEmail(source.getEmail());
        if (!StringUtils.hasText(target.getIdentityNo())  && StringUtils.hasText(source.getIdentityNo()))
            target.setIdentityNo(source.getIdentityNo());
        if (!StringUtils.hasText(target.getGender())      && StringUtils.hasText(source.getGender()))
            target.setGender(source.getGender());
        if (target.getDateOfBirth() == null               && source.getDateOfBirth() != null)
            target.setDateOfBirth(source.getDateOfBirth());
        if (!StringUtils.hasText(target.getCustomerType()) && StringUtils.hasText(source.getCustomerType()))
            target.setCustomerType(source.getCustomerType());
        if (!StringUtils.hasText(target.getProvinceCode()) && StringUtils.hasText(source.getProvinceCode())) {
            target.setProvinceCode(source.getProvinceCode());
            target.setProvinceName(source.getProvinceName());
        }
        if (!StringUtils.hasText(target.getUnitCode())    && StringUtils.hasText(source.getUnitCode())) {
            target.setUnitCode(source.getUnitCode());
            target.setUnitName(source.getUnitName());
        }
    }

    private Mono<MasterProfile> loadProfile(Long id) {
        return masterProfileRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("NOT_FOUND", "Master profile not found: " + id)));
    }

    private Mono<MasterProfile> loadActiveProfile(Long id) {
        return loadProfile(id).flatMap(p -> isMergedOrDeleted(p.getStatus())
                ? Mono.error(new BusinessException("INVALID_PROFILE",
                        "Profile " + id + " is MERGED or DELETED and cannot be used for matching"))
                : Mono.just(p));
    }

    private Mono<ProfileMatchCandidate> loadCandidate(Long id) {
        return candidateRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("NOT_FOUND", "Match candidate not found: " + id)));
    }

    private Mono<ProfileMatchCandidate> validatePending(ProfileMatchCandidate candidate) {
        if (candidate.getStatus() != STATUS_PENDING) {
            return Mono.error(new BusinessException("INVALID_STATUS",
                    "Candidate must be in PENDING status. Current: " + candidate.getStatus()));
        }
        return Mono.just(candidate);
    }

    private boolean isMergedOrDeleted(Short status) {
        return status != null && (status == PROFILE_MERGED || status == PROFILE_DELETED);
    }

    private static <T> Mono<Optional<T>> optional(Mono<T> mono) {
        return mono.map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    // =====================================================================
    // RESPONSE MAPPING
    // =====================================================================

    private ProfileMatchCandidateResponse toResponse(ProfileMatchCandidate candidate,
                                                      MasterProfile left, MasterProfile right,
                                                      List<ProfileMatchReason> reasons) {
        return ProfileMatchCandidateResponse.builder()
                .id(candidate.getId())
                .matchScore(candidate.getMatchScore())
                .matchScorePercent(formatPercent(candidate.getMatchScore()))
                .matchLevel(candidate.getMatchLevel())
                .matchLevelText(matchLevelText(candidate.getMatchLevel()))
                .status(candidate.getStatus())
                .statusText(statusText(candidate.getStatus()))
                .decisionBy(candidate.getDecisionBy())
                .decisionAt(candidate.getDecisionAt())
                .mergeRequestId(candidate.getMergeRequestId())
                .leftProfile(toSideResponse(left,  candidate.getLeftSourceSystem(),  candidate.getLeftSourceCustomerId()))
                .rightProfile(toSideResponse(right, candidate.getRightSourceSystem(), candidate.getRightSourceCustomerId()))
                .reasons(reasons.stream().map(this::toReasonResponse).collect(Collectors.toList()))
                .created(candidate.getCreated())
                .build();
    }

    private ProfileMatchSideResponse toSideResponse(MasterProfile p, String ss, String scId) {
        if (p == null) return ProfileMatchSideResponse.builder()
                .sourceSystem(ss).sourceCustomerId(scId).build();
        return ProfileMatchSideResponse.builder()
                .masterProfileId(p.getId())
                .profileCode(p.getProfileCode())
                .sourceSystem(ss)
                .sourceCustomerId(scId)
                .fullName(p.getFullName())
                .phone(p.getPhone())
                .email(p.getEmail())
                .identityNo(p.getIdentityNo())
                .customerType(p.getCustomerType())
                .provinceCode(p.getProvinceCode())
                .provinceName(p.getProvinceName())
                .unitCode(p.getUnitCode())
                .unitName(p.getUnitName())
                .build();
    }

    private ProfileMatchReasonResponse toReasonResponse(ProfileMatchReason r) {
        return ProfileMatchReasonResponse.builder()
                .id(r.getId())
                .reasonType(r.getReasonType())
                .reasonMessage(r.getReasonMessage())
                .leftValue(r.getLeftValue())
                .rightValue(r.getRightValue())
                .score(r.getScore())
                .build();
    }

    private String formatPercent(BigDecimal score) {
        if (score == null) return "0%";
        return score.setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String matchLevelText(String ml) {
        if (ml == null) return "";
        return switch (ml) {
            case "VERY_HIGH" -> "Rất cao";
            case "HIGH"      -> "Cao";
            case "MEDIUM"    -> "Trung bình";
            case "LOW"       -> "Thấp";
            default          -> ml;
        };
    }

    private String statusText(Short s) {
        if (s == null) return "";
        return switch (s) {
            case 0  -> "Chờ xử lý";
            case 1  -> "Đã merge";
            case 2  -> "Đã bỏ qua";
            case 3  -> "Đã từ chối";
            case 4  -> "Hết hạn";
            default -> String.valueOf(s);
        };
    }

}
