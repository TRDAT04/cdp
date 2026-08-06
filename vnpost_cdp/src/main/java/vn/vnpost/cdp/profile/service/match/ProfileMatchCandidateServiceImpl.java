package vn.vnpost.cdp.profile.service.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.common.utils.IdentityUtils;
import vn.vnpost.cdp.customer_event.repository.CustomerEventRepository;
import vn.vnpost.cdp.profile.dto.match.*;
import vn.vnpost.cdp.profile.entity.*;
import vn.vnpost.cdp.profile.enums.CustomerType;
import vn.vnpost.cdp.profile.repository.*;
import vn.vnpost.cdp.security.SecurityUtils;
import vn.vnpost.cdp.unomi.service.UnomiService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProfileMatchCandidateServiceImpl implements ProfileMatchCandidateService {

    private static final short STATUS_PENDING  = 0;
    private static final short STATUS_MERGED   = 1;
    private static final short STATUS_IGNORED  = 2;
    private static final short STATUS_REJECTED = 3;
    private static final short STATUS_EXPIRED  = 4;

    private static final short PROFILE_MERGED  = 3;
    private static final short PROFILE_DELETED = 5;

    // Mọi con số dưới đây định nghĩa ở IdentityMatchThresholds — nơi duy nhất, vì API bảng rule
    // cũng đọc từ đó. Xem Javadoc của lớp ấy để biết lý do chọn từng mốc.

    /** Dưới ngưỡng này thì nhóm được đánh dấu "tin cậy thấp" để UI cảnh báo. */
    private static final BigDecimal LOW_CONFIDENCE_THRESHOLD =
            BigDecimal.valueOf(IdentityMatchThresholds.LOW_CONFIDENCE_FLAG_SCORE);

    private static final BigDecimal DETERMINISTIC_SCORE_IDENTITY =
            BigDecimal.valueOf(IdentityMatchThresholds.DETERMINISTIC_IDENTITY_NO);
    private static final BigDecimal DETERMINISTIC_SCORE_TAX =
            BigDecimal.valueOf(IdentityMatchThresholds.DETERMINISTIC_TAX_CODE);
    private static final BigDecimal DETERMINISTIC_SCORE_TYPED_ID =
            BigDecimal.valueOf(IdentityMatchThresholds.DETERMINISTIC_TYPED_ID);

    /** Các loại identity_link được coi là khóa duy nhất do nguồn cấp — KHÔNG gồm DEVICE_ID/COOKIE_ID
     *  (để dành cho Probabilistic Matching sau này, tránh false-positive khi auto-merge). */
    private static final List<String> UNIQUE_TYPED_IDENTITY_TYPES =
            IdentityMatchThresholds.UNIQUE_TYPED_IDENTITY_TYPES;

    /** Ngưỡng tên "không lệch quá xa", nhất quán với scorer và ProfileMergeDecisionService. */
    private static final double NAME_SIMILARITY_MIN = IdentityMatchThresholds.NAME_SIMILARITY_MIN;

    /**
     * Sàn tạo candidate. Dưới ngưỡng này, match quá yếu (VD chỉ khớp tên, 30đ) không đủ cơ sở để
     * gợi ý — nhất là với tên tiếng Việt vốn rất dễ trùng lặp (VD "Nguyễn Văn A"), sẽ sinh nhiễu.
     * 35 vẫn bắt được "chỉ SĐT trùng" (40đ) và "chỉ email trùng" (35đ) làm gợi ý tin cậy thấp thay
     * vì bị âm thầm bỏ qua như trước.
     */
    private static final BigDecimal MIN_CANDIDATE_SCORE =
            BigDecimal.valueOf(IdentityMatchThresholds.LOW_CONFIDENCE_SCORE);

    /** Một giá trị khóa khớp nhiều hơn ngưỡng này thì coi như khóa rác, không dùng để ghép cặp. */
    private static final int MAX_PROFILES_PER_KEY = IdentityMatchThresholds.MAX_PROFILES_PER_KEY;

    /** Candidate từng bị IGNORED/REJECTED chỉ được tạo lại nếu điểm mới cao hơn ít nhất mức này. */
    private static final BigDecimal MIN_SCORE_IMPROVEMENT =
            BigDecimal.valueOf(IdentityMatchThresholds.RECREATE_SCORE_IMPROVEMENT);

    private static final List<String> SOURCE_PRIORITY = List.of("CRM", "MYVNPOST", "PORTAL", "CMS");

    private final ProfileMatchCandidateRepository candidateRepository;
    private final ProfileMatchReasonRepository reasonRepository;
    private final MasterProfileRepository masterProfileRepository;
    private final ProfileIdentityLinkRepository identityLinkRepository;
    private final ProfileAttributeValueRepository attributeValueRepository;
    private final ProfileChangeLogRepository changeLogRepository;
    private final ProfileMergeRequestRepository mergeRequestRepository;
    private final ProfileUnomiSyncLogRepository unomiSyncLogRepository;
    private final ProfileSourceRecordRepository sourceRecordRepository;
    private final ProfileMergeConflictRepository conflictRepository;
    private final CustomerEventRepository customerEventRepository;
    private final ProfileMatchScoreService scoreService;
    private final UnomiService unomiService;
    private final ObjectMapper objectMapper;


    // =====================================================================
    // CREATE CANDIDATE
    // =====================================================================

    @Override
    public ProfileMatchCandidateResponse createCandidate(Long leftId, Long rightId) {
        if (Objects.equals(leftId, rightId)) {
            throw new BusinessException("INVALID_INPUT", "Left and right profiles must be different");
        }
        MasterProfile left  = loadActiveProfile(leftId);
        MasterProfile right = loadActiveProfile(rightId);

        ProfileMatchScoreResult scoreResult = resolveMatch(left, right);
        BigDecimal newScore = scoreResult.getScore();

        if (newScore.compareTo(MIN_CANDIDATE_SCORE) < 0) {
            throw new BusinessException("SCORE_TOO_LOW", "Match score is too low to create a candidate");
        }

        Optional<ProfileMatchCandidate> existingOpt = findExistingCandidate(leftId, rightId);
        if (existingOpt.isPresent()) {
            ProfileMatchCandidate existing = existingOpt.get();
            short existingStatus = existing.getStatus();

            if (existingStatus == STATUS_PENDING || existingStatus == STATUS_MERGED) {
                List<ProfileMatchReason> reasons = reasonRepository.findByMatchCandidateId(existing.getId());
                return toResponse(existing, left, right, reasons);
            }

            if (existingStatus == STATUS_IGNORED || existingStatus == STATUS_REJECTED) {
                BigDecimal diff = newScore.subtract(existing.getMatchScore());
                if (diff.compareTo(MIN_SCORE_IMPROVEMENT) < 0) {
                    throw new BusinessException("SCORE_NOT_IMPROVED",
                            "New score must be at least 10 points higher than previous ignored/rejected candidate to recreate");
                }
                existing.setStatus(STATUS_EXPIRED);
                candidateRepository.save(existing);
            }
        }

        ProfileMatchCandidate saved = persistCandidateWithReasons(left, right, scoreResult);
        List<ProfileMatchReason> reasons = reasonRepository.findByMatchCandidateId(saved.getId());
        return toResponse(saved, left, right, reasons);
    }

    // =====================================================================
    // GET BY ID
    // =====================================================================

    @Override
    @Transactional(readOnly = true)
    public ProfileMatchCandidateResponse getById(Long id) {
        ProfileMatchCandidate candidate = loadCandidate(id);
        MasterProfile left  = masterProfileRepository.findById(candidate.getLeftMasterProfileId()).orElse(null);
        MasterProfile right = masterProfileRepository.findById(candidate.getRightMasterProfileId()).orElse(null);
        List<ProfileMatchReason> reasons = reasonRepository.findByMatchCandidateId(id);
        return toResponse(candidate, left, right, reasons);
    }

    // =====================================================================
    // SEARCH
    // =====================================================================

    @Override
    @Transactional(readOnly = true)
    public List<ProfileMatchCandidateResponse> search(ProfileMatchCandidateSearchRequest req) {
        Specification<ProfileMatchCandidate> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (req.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), req.getStatus()));
            }
            if (StringUtils.hasText(req.getMatchLevel())) {
                predicates.add(cb.equal(root.get("matchLevel"), req.getMatchLevel()));
            }
            if (req.getMinScore() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("matchScore"), req.getMinScore()));
            }
            if (StringUtils.hasText(req.getSourceSystem())) {
                predicates.add(cb.or(
                        cb.equal(root.get("leftSourceSystem"), req.getSourceSystem()),
                        cb.equal(root.get("rightSourceSystem"), req.getSourceSystem())
                ));
            }
            if (req.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("created"), req.getFromDate()));
            }
            if (req.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("created"), req.getToDate()));
            }
            if (StringUtils.hasText(req.getKeyword())) {
                // ProfileMatchCandidate chỉ giữ id thô (không có association sang MasterProfile)
                // nên không join được — dùng EXISTS subquery khớp keyword vào một trong hai vế.
                String kw = "%" + req.getKeyword().trim().toLowerCase() + "%";
                Subquery<Long> sub = query.subquery(Long.class);
                Root<MasterProfile> mp = sub.from(MasterProfile.class);
                sub.select(mp.get("id")).where(cb.and(
                        cb.or(
                                cb.equal(mp.get("id"), root.get("leftMasterProfileId")),
                                cb.equal(mp.get("id"), root.get("rightMasterProfileId"))
                        ),
                        cb.or(
                                cb.like(cb.lower(mp.get("fullName")),    kw),
                                cb.like(cb.lower(mp.get("profileCode")), kw),
                                cb.like(cb.lower(mp.get("phone")),       kw),
                                cb.like(cb.lower(mp.get("taxCode")),     kw)
                        )
                ));
                predicates.add(cb.exists(sub));
            }
            query.orderBy(cb.desc(root.get("matchScore")), cb.desc(root.get("created")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return candidateRepository.findAll(spec).stream()
                .map(this::toResponseWithLookup)
                .collect(Collectors.toList());
    }

    // =====================================================================
    // GROUPED PENDING — màn "Đối soát định danh"
    // =====================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<ProfileMatchGroupResponse> searchPendingGroups(ProfileMatchGroupSearchRequest req,
                                                               Pageable pageable) {
        String keyword = StringUtils.hasText(req.getKeyword())
                ? "%" + req.getKeyword().trim() + "%"
                : null;

        long total = candidateRepository.countPendingGroups(keyword);
        if (total == 0) {
            log.info("ProfileMatchCandidateServiceImpl - searchPendingGroups: keyword={}, no pending group", keyword);
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<Object[]> rows = candidateRepository.findPendingGroups(
                keyword, pageable.getPageSize(), pageable.getOffset());

        List<ProfileMatchGroupResponse> content = rows.stream()
                .map(this::toGroupResponse)
                .collect(Collectors.toList());

        // Khoá khớp chỉ nạp cho các hồ sơ của TRANG hiện tại — một query phụ, không phụ thuộc
        // tổng số candidate trong DB.
        attachMatchedKeys(content);

        log.info("ProfileMatchCandidateServiceImpl - searchPendingGroups: keyword={}, page={}, size={}, "
                        + "returned={}, totalGroups={}",
                keyword, pageable.getPageNumber(), pageable.getPageSize(), content.size(), total);

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Map một dòng aggregate: {@code [id, profile_code, full_name, customer_type, phone, tax_code,
     * identity_no, pending_count, max_score, min_score]}.
     */
    private ProfileMatchGroupResponse toGroupResponse(Object[] row) {
        String customerType = asString(row[3]);
        BigDecimal maxScore = asBigDecimal(row[8]);
        BigDecimal minScore = asBigDecimal(row[9]);

        return ProfileMatchGroupResponse.builder()
                .masterProfileId(asLong(row[0]))
                .profileCode(asString(row[1]))
                .fullName(asString(row[2]))
                .customerType(customerType)
                .customerTypeText(CustomerType.textOf(customerType))
                .phone(asString(row[4]))
                .taxCode(asString(row[5]))
                .identityNo(asString(row[6]))
                .pendingCount(asLong(row[7]))
                .maxScore(maxScore)
                .maxScorePercent(formatPercent(maxScore))
                .maxMatchLevel(resolveMatchLevel(maxScore))
                .maxMatchLevelText(matchLevelText(resolveMatchLevel(maxScore)))
                .matchedKeys(new ArrayList<>())
                .matchedKeysText(new ArrayList<>())
                .hasLowConfidence(minScore != null
                        && minScore.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0)
                .build();
    }

    private void attachMatchedKeys(List<ProfileMatchGroupResponse> groups) {
        if (groups.isEmpty()) {
            return;
        }
        List<Long> profileIds = groups.stream()
                .map(ProfileMatchGroupResponse::getMasterProfileId)
                .collect(Collectors.toList());

        Map<Long, List<String>> byProfile = new HashMap<>();
        for (Object[] row : candidateRepository.findMatchedReasonTypes(profileIds)) {
            byProfile.computeIfAbsent(asLong(row[0]), k -> new ArrayList<>()).add(asString(row[1]));
        }

        for (ProfileMatchGroupResponse g : groups) {
            List<String> keys = byProfile.getOrDefault(g.getMasterProfileId(), List.of());
            g.setMatchedKeys(new ArrayList<>(keys));
            g.setMatchedKeysText(keys.stream()
                    .map(this::reasonTypeText)
                    .distinct()
                    .collect(Collectors.toList()));
        }
    }

    // =====================================================================
    // LIST
    // =====================================================================



    @Override
    @Transactional(readOnly = true)
    public List<ProfileMatchCandidateResponse> listByStatus(Short status) {
        return candidateRepository.findByStatus(status)
                .stream().map(this::toResponseWithLookup).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileMatchCandidateResponse> listByProfile(Long masterProfileId) {
        return candidateRepository
                .findByLeftMasterProfileIdOrRightMasterProfileId(masterProfileId, masterProfileId)
                .stream().map(this::toResponseWithLookup).collect(Collectors.toList());
    }

    // =====================================================================
    // IGNORE / REJECT
    // =====================================================================

    @Override
    public ProfileMatchCandidateResponse ignore(Long id) {
        ProfileMatchCandidate candidate = loadCandidate(id);
        validatePending(candidate);
        candidate.setStatus(STATUS_IGNORED);
        candidate.setDecisionBy(SecurityUtils.getCurrentUsername().orElse("system"));
        candidate.setDecisionAt(LocalDateTime.now());
        candidateRepository.save(candidate);
        log.info("ProfileMatchCandidateServiceImpl - IGNORED candidate id={}", id);
        return getById(id);
    }

    @Override
    public ProfileMatchCandidateResponse reject(Long id) {
        ProfileMatchCandidate candidate = loadCandidate(id);
        validatePending(candidate);
        candidate.setStatus(STATUS_REJECTED);
        candidate.setDecisionBy(SecurityUtils.getCurrentUsername().orElse("system"));
        candidate.setDecisionAt(LocalDateTime.now());
        candidateRepository.save(candidate);
        log.info("ProfileMatchCandidateServiceImpl - REJECTED candidate id={}", id);
        return getById(id);
    }

    // =====================================================================
    // MERGE
    // =====================================================================

    @Override
    public ProfileMatchCandidateResponse merge(Long id, ProfileCandidateMergeRequest request) {
        ProfileMatchCandidate candidate = loadCandidate(id);
        validatePending(candidate);

        // Bắt buộc cả hai vế còn ACTIVE: một merge khác (chạy trước) có thể đã biến 1 trong 2
        // hồ sơ thành MERGED/DELETED. Nếu vẫn cho merge tiếp sẽ tạo chuỗi merge chồng chéo và
        // ghi đè mergedIntoProfileId sai.
        MasterProfile left  = loadActiveProfile(candidate.getLeftMasterProfileId());
        MasterProfile right = loadActiveProfile(candidate.getRightMasterProfileId());

        // Determine target and source
        MasterProfile target;
        MasterProfile source;
        if (request.getTargetMasterProfileId() != null) {
            if (!request.getTargetMasterProfileId().equals(left.getId())
                    && !request.getTargetMasterProfileId().equals(right.getId())) {
                throw new BusinessException("INVALID_TARGET",
                        "targetMasterProfileId must be either leftMasterProfileId or rightMasterProfileId");
            }
            target = request.getTargetMasterProfileId().equals(left.getId()) ? left : right;
            source = (target == left) ? right : left;
        } else {
            target = chooseTarget(left, right, candidate);
            source = (target == left) ? right : left;
        }

        String actor = SecurityUtils.getCurrentUsername().orElse("system");
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
        mergeReq = mergeRequestRepository.save(mergeReq);
        log.info("ProfileMatchCandidateServiceImpl - mergeRequest id={} created", mergeReq.getId());

        // 6. Merge data (fill blanks only)
        mergeProfileData(target, source);
        target.setLastMergedAt(now);
        masterProfileRepository.save(target);

        // 8. Copy identity links
        copyIdentityLinks(source.getId(), target.getId(), actor, now);

        // 9. Copy attribute values
        copyAttributeValues(source.getId(), target.getId());

        // 9b. Re-point dữ liệu tham chiếu source sang target. Các tab tra cứu đều query thẳng
        // theo masterProfileId (không đi theo mergedIntoProfileId), nên nếu bỏ bước này thì
        // event / source record / conflict của source tồn tại trong DB nhưng không còn hiển thị
        // ở bất kỳ hồ sơ nào sau merge.
        reassignReferencedData(source.getId(), target.getId());

        // 10. Update source profile
        source.setStatus((short) PROFILE_MERGED);
        source.setMergedIntoProfileId(target.getId());
        masterProfileRepository.save(source);

        // 11. Write change log
        ProfileChangeLog cl = new ProfileChangeLog();
        cl.setMasterProfileId(target.getId());
        cl.setSourceSystem(candidate.getLeftMasterProfileId().equals(source.getId())
                ? candidate.getLeftSourceSystem() : candidate.getRightSourceSystem());
        cl.setEventType("ADMIN_MERGE");
        cl.setPropertyName("PROFILE_MERGE");
        cl.setOldValue(source.getProfileCode());
        cl.setNewValue(target.getProfileCode());
        cl.setSelectedValue(target.getProfileCode());
        cl.setOldSource(candidate.getLeftMasterProfileId().equals(source.getId())
                ? candidate.getLeftSourceSystem() : candidate.getRightSourceSystem());
        cl.setNewSource(candidate.getLeftMasterProfileId().equals(target.getId())
                ? candidate.getLeftSourceSystem() : candidate.getRightSourceSystem());
        cl.setMergeStrategy("ADMIN_DECISION");
        cl.setReason(request.getMergeReason());
        cl.setChangedBy(actor);
        cl.setChangedAt(now);
        changeLogRepository.save(cl);

        // 12. Update candidate
        candidate.setStatus(STATUS_MERGED);
        candidate.setDecisionBy(actor);
        candidate.setDecisionAt(now);
        candidate.setMergeRequestId(mergeReq.getId());
        candidateRepository.save(candidate);

        // 12b. Vô hiệu hoá các candidate PENDING khác còn trỏ tới source (profile vừa "chết").
        expireStaleCandidatesForMergedProfile(source.getId(), id, actor, now);

        // 13. Sync target to Unomi
        syncToUnomi(target, "MERGE");

        // 13b. Đẩy cả source lên Unomi để bên đó biết hồ sơ này đã MERGED (status + mergedIntoProfileId).
        // Unomi không có API delete trong UnomiClient, nên đây là cách tránh đếm trùng khách hàng
        // ở segment/campaign mà không cần đổi contract phía Unomi.
        syncToUnomi(source, "MERGE_SOURCE");

        log.info("ProfileMatchCandidateServiceImpl - MERGED candidate id={}, source={}, target={}",
                id, source.getId(), target.getId());
        return getById(id);
    }

    // =====================================================================
    // CREATE CANDIDATE BETWEEN TWO KNOWN PROFILES (CREATE_MATCH_CANDIDATE flow)
    // =====================================================================

    @Override
    public ProfileMatchCandidate createCandidateBetweenProfiles(Long existingProfileId,
                                                                 Long newProfileId,
                                                                 NormalizedProfileData incomingData,
                                                                 ProfileSourceRecord sourceRecord) {
        log.info("ProfileMatchCandidateServiceImpl - createCandidateBetweenProfiles: existing={}, new={}",
                existingProfileId, newProfileId);

        if (Objects.equals(existingProfileId, newProfileId)) {
            throw new BusinessException("INVALID_INPUT", "existingProfileId and newProfileId must be different");
        }

        MasterProfile existing = masterProfileRepository.findById(existingProfileId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND",
                        "Existing master profile not found: " + existingProfileId));
        MasterProfile newProfile = masterProfileRepository.findById(newProfileId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND",
                        "New master profile not found: " + newProfileId));

        if (existing.getStatus() == PROFILE_MERGED || existing.getStatus() == PROFILE_DELETED) {
            throw new BusinessException("INVALID_PROFILE",
                    "Existing profile " + existingProfileId + " is MERGED or DELETED");
        }
        if (newProfile.getStatus() == PROFILE_MERGED || newProfile.getStatus() == PROFILE_DELETED) {
            throw new BusinessException("INVALID_PROFILE",
                    "New profile " + newProfileId + " is MERGED or DELETED");
        }

        // Return existing PENDING/MERGED candidate if one already exists between the pair
        if (candidateRepository.existsPendingOrMergedBetween(existingProfileId, newProfileId)) {
            List<ProfileMatchCandidate> existing2 = candidateRepository.findBetween(existingProfileId, newProfileId);
            if (!existing2.isEmpty()) {
                log.info("ProfileMatchCandidateServiceImpl - candidate already exists between ({},{}), returning existing",
                        existingProfileId, newProfileId);
                return existing2.get(0);
            }
        }

        // Calculate score
        ProfileMatchScoreResult scoreResult = scoreService.calculate(existing, newProfile);

        // Build candidate
        ProfileIdentityLink existingLink = getPrimaryLink(existingProfileId);
        ProfileIdentityLink newLink     = getPrimaryLink(newProfileId);

        ProfileMatchCandidate candidate = new ProfileMatchCandidate();
        candidate.setLeftMasterProfileId(existingProfileId);
        candidate.setRightMasterProfileId(newProfileId);
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
        candidate = candidateRepository.save(candidate);

        final Long candidateId = candidate.getId();
        LocalDateTime now = LocalDateTime.now();
        List<ProfileMatchReason> reasons = scoreResult.getReasons().stream().map(item -> {
            ProfileMatchReason r = new ProfileMatchReason();
            r.setMatchCandidateId(candidateId);
            r.setReasonType(item.getReasonType());
            r.setReasonMessage(item.getReasonMessage());
            r.setLeftValue(item.getLeftValue());
            r.setRightValue(item.getRightValue());
            r.setScore(item.getScore());
            return r;
        }).collect(Collectors.toList());
        reasonRepository.saveAll(reasons);

        log.info("ProfileMatchCandidateServiceImpl - created candidate id={} between ({},{}), score={}",
                candidateId, existingProfileId, newProfileId, scoreResult.getScore());
        return candidate;
    }

    // =====================================================================
    // DETECT AND CREATE CANDIDATES
    // =====================================================================

    @Override
    public void detectAndCreateCandidatesForProfile(Long masterProfileId) {
        Optional<MasterProfile> opt = masterProfileRepository.findById(masterProfileId);
        if (opt.isEmpty()) {
            log.warn("ProfileMatchCandidateServiceImpl - detectAndCreate: profile {} not found", masterProfileId);
            return;
        }
        MasterProfile profile = opt.get();

        if (profile.getStatus() != null
                && (profile.getStatus() == PROFILE_MERGED || profile.getStatus() == PROFILE_DELETED)) {
            log.debug("ProfileMatchCandidateServiceImpl - detectAndCreate: skip merged/deleted profile {}", masterProfileId);
            return;
        }

        Set<Long> candidateProfileIds = new LinkedHashSet<>();

        if (StringUtils.hasText(profile.getIdentityNo())) {
            addToPool(masterProfileRepository.findByIdentityNo(profile.getIdentityNo()),
                    masterProfileId, candidateProfileIds, "CCCD");
        }
        // MST phải có trong pool, nếu không nhánh deterministic MST của resolveMatch() không bao giờ
        // được kích hoạt từ luồng detect: hai hồ sơ doanh nghiệp trùng MST nhưng khác
        // CCCD/SĐT/email sẽ không bao giờ được ghép cặp để so.
        if (StringUtils.hasText(profile.getTaxCode())) {
            addToPool(masterProfileRepository.findByTaxCode(profile.getTaxCode()),
                    masterProfileId, candidateProfileIds, "MST");
        }
        if (StringUtils.hasText(profile.getPhone())) {
            addToPool(masterProfileRepository.findByPhone(profile.getPhone()),
                    masterProfileId, candidateProfileIds, "SĐT");
        }
        if (StringUtils.hasText(profile.getEmail())) {
            addToPool(masterProfileRepository.findByEmail(profile.getEmail()),
                    masterProfileId, candidateProfileIds, "email");
        }
        // Khóa duy nhất do nguồn cấp (KHL_CODE/CRM_ID/POST_ID/APP_USER_ID/PAYMENT_ID) — mirror
        // FP2c của ProfileMergeDecisionService. Thiếu bước này thì 2 hồ sơ trùng PostID nhưng khác
        // CCCD/MST/SĐT/email sẽ không bao giờ được ghép cặp để so ở luồng admin.
        for (ProfileIdentityLink ownLink : identityLinkRepository.findByMasterProfileIdAndStatus(masterProfileId, (short) 1)) {
            if (!UNIQUE_TYPED_IDENTITY_TYPES.contains(ownLink.getIdentityType())) {
                continue;
            }
            identityLinkRepository.findByIdentityTypeAndIdentityValue(ownLink.getIdentityType(), ownLink.getIdentityValue())
                    .stream()
                    .filter(l -> l.getStatus() != null && l.getStatus() == 1)
                    .map(ProfileIdentityLink::getMasterProfileId)
                    .filter(id -> !id.equals(masterProfileId))
                    .forEach(candidateProfileIds::add);
        }

        log.info("ProfileMatchCandidateServiceImpl - detectAndCreate: profile={}, candidate pool size={}",
                masterProfileId, candidateProfileIds.size());

        for (Long candidateProfileId : candidateProfileIds) {
            try {
                Optional<MasterProfile> candidateOpt = masterProfileRepository.findById(candidateProfileId);
                if (candidateOpt.isEmpty()) continue;
                MasterProfile candidateProfile = candidateOpt.get();

                if (candidateProfile.getStatus() != null
                        && (candidateProfile.getStatus() == PROFILE_MERGED
                        || candidateProfile.getStatus() == PROFILE_DELETED)) {
                    continue;
                }

                ProfileMatchScoreResult scoreResult = resolveMatch(profile, candidateProfile);
                if (scoreResult.getScore().compareTo(MIN_CANDIDATE_SCORE) < 0) {
                    log.debug("ProfileMatchCandidateServiceImpl - score {} too low for pair ({},{})",
                            scoreResult.getScore(), masterProfileId, candidateProfileId);
                    continue;
                }

                // Check for existing PENDING or MERGED
                boolean hasPending = candidateRepository.existsByLeftMasterProfileIdAndRightMasterProfileIdAndStatus(
                        masterProfileId, candidateProfileId, STATUS_PENDING)
                        || candidateRepository.existsByRightMasterProfileIdAndLeftMasterProfileIdAndStatus(
                        masterProfileId, candidateProfileId, STATUS_PENDING);
                boolean hasMerged = candidateRepository.existsByLeftMasterProfileIdAndRightMasterProfileIdAndStatus(
                        masterProfileId, candidateProfileId, STATUS_MERGED)
                        || candidateRepository.existsByRightMasterProfileIdAndLeftMasterProfileIdAndStatus(
                        masterProfileId, candidateProfileId, STATUS_MERGED);

                if (hasPending || hasMerged) {
                    log.debug("ProfileMatchCandidateServiceImpl - already has pending/merged candidate for pair ({},{})",
                            masterProfileId, candidateProfileId);
                    continue;
                }

                // Check IGNORED/REJECTED — only create if score is >= 10 higher
                Optional<ProfileMatchCandidate> existingOpt = findExistingCandidate(masterProfileId, candidateProfileId);
                if (existingOpt.isPresent()) {
                    ProfileMatchCandidate existing = existingOpt.get();
                    if (existing.getStatus() == STATUS_IGNORED || existing.getStatus() == STATUS_REJECTED) {
                        BigDecimal diff = scoreResult.getScore().subtract(existing.getMatchScore());
                        if (diff.compareTo(MIN_SCORE_IMPROVEMENT) < 0) {
                            continue;
                        }
                        existing.setStatus(STATUS_EXPIRED);
                        candidateRepository.save(existing);
                    }
                }

                persistCandidateWithReasons(profile, candidateProfile, scoreResult);
                log.info("ProfileMatchCandidateServiceImpl - created candidate for pair ({},{})",
                        masterProfileId, candidateProfileId);

            } catch (Exception ex) {
                log.error("ProfileMatchCandidateServiceImpl - error for pair ({},{}): {}",
                        masterProfileId, candidateProfileId, ex.getMessage(), ex);
            }
        }
    }

    // =====================================================================
    // PRIVATE HELPERS
    // =====================================================================

    /**
     * Deterministic-first matching cho luồng ADMIN, mirror đúng thứ tự đã có sẵn ở
     * {@code ProfileMergeDecisionService} của luồng ingestion: CCCD → MST → probabilistic.
     *
     * <p>Lý do tồn tại: {@link ProfileMatchScoreService} chỉ cộng dồn điểm, nên hai hồ sơ trùng
     * CCCD (hoặc trùng MST) hoàn toàn chỉ được 50/100 — không đạt ngưỡng auto-merge và bị xếp
     * ngang một cặp chỉ trùng vài tín hiệu yếu. Khóa mạnh trùng khớp là bằng chứng deterministic
     * nên điểm được nâng thẳng lên {@link #DETERMINISTIC_SCORE_IDENTITY} /
     * {@link #DETERMINISTIC_SCORE_TAX}.
     *
     * <p>Danh sách reason của scorer được GIỮ NGUYÊN (không rút còn một dòng): màn đối chiếu cần
     * thấy đủ bằng chứng, và {@code matchedKeys} ở màn nhóm lấy trực tiếp từ bảng reason nên rút
     * bớt sẽ làm mất khoá khớp trên UI.
     *
     * <p>Nhánh XUNG ĐỘT khóa mạnh cố tình KHÔNG tự đặt điểm: trả nguyên kết quả probabilistic, vì
     * scorer đã set {@code identityConflict=true} khiến {@code autoMergeRecommended} luôn false,
     * đồng thời giữ đúng hành vi hiện tại của ngưỡng 70 — cặp khác CCCD chỉ vô tình trùng SĐT vẫn
     * không sinh candidate nhiễu.
     */
    private ProfileMatchScoreResult resolveMatch(MasterProfile left, MasterProfile right) {
        ProfileMatchScoreResult scoreResult = scoreService.calculate(left, right);

        // 1. CCCD — khóa mạnh nhất, ưu tiên tuyệt đối trên MST: khớp hay lệch CCCD đều là quyết
        // định cuối, không xét MST nữa (giống ingestion).
        String leftIdentityNo  = IdentityUtils.normalizeIdentityNo(left.getIdentityNo());
        String rightIdentityNo = IdentityUtils.normalizeIdentityNo(right.getIdentityNo());
        if (StringUtils.hasText(leftIdentityNo) && StringUtils.hasText(rightIdentityNo)) {
            if (!leftIdentityNo.equals(rightIdentityNo)) {
                log.info("ProfileMatchCandidateServiceImpl - resolveMatch pair ({},{}): identityNo conflict "
                                + "→ giữ điểm probabilistic score={}, autoMerge={}",
                        left.getId(), right.getId(), scoreResult.getScore(),
                        scoreResult.isAutoMergeRecommended());
                return scoreResult;
            }
            return promoteDeterministic(scoreResult, DETERMINISTIC_SCORE_IDENTITY,
                    "identityNo match", left, right);
        }

        // 2. MST — định danh mạnh của khách doanh nghiệp. Chỉ xét khi không so được CCCD.
        String leftTaxCode  = IdentityUtils.normalizeIdentityNo(left.getTaxCode());
        String rightTaxCode = IdentityUtils.normalizeIdentityNo(right.getTaxCode());
        if (StringUtils.hasText(leftTaxCode) && StringUtils.hasText(rightTaxCode)) {
            if (!leftTaxCode.equals(rightTaxCode)) {
                log.info("ProfileMatchCandidateServiceImpl - resolveMatch pair ({},{}): taxCode conflict "
                                + "→ giữ điểm probabilistic score={}, autoMerge={}",
                        left.getId(), right.getId(), scoreResult.getScore(),
                        scoreResult.isAutoMergeRecommended());
                return scoreResult;
            }
            if (nameCompatible(left.getFullName(), right.getFullName())) {
                return promoteDeterministic(scoreResult, DETERMINISTIC_SCORE_TAX,
                        "taxCode match (name ok)", left, right);
            }
            // Khớp MST nhưng tên lệch xa: rất có thể là hai nhân viên khai chung MST/SĐT/email
            // công ty. Điểm cộng dồn (MST 50 + SĐT 40 + email 35) tự vượt 95 nên phải chặn tay,
            // mirror NEED_REVIEW của ingestion.
            scoreResult.setAutoMergeRecommended(false);
            log.info("ProfileMatchCandidateServiceImpl - resolveMatch pair ({},{}): taxCode match nhưng tên "
                            + "lệch > 25% → chặn auto-merge, score={}",
                    left.getId(), right.getId(), scoreResult.getScore());
            return scoreResult;
        }

        // 3. Khóa duy nhất do nguồn cấp (KHL_CODE/CRM_ID/POST_ID/APP_USER_ID/PAYMENT_ID) — chỉ xét
        // khi không so được CCCD lẫn MST. Mirror FP2c của ProfileMergeDecisionService (ingestion).
        if (matchedByUniqueTypedId(left, right)) {
            return promoteDeterministic(scoreResult, DETERMINISTIC_SCORE_TYPED_ID,
                    "typed identifier match", left, right);
        }

        // 4. Không có khóa mạnh nào so được cả hai bên → probabilistic thuần như trước.
        return scoreResult;
    }

    /**
     * Kiểm tra 2 hồ sơ có cùng chia sẻ ít nhất một giá trị ACTIVE của cùng một loại khóa duy nhất
     * do nguồn cấp ({@link #UNIQUE_TYPED_IDENTITY_TYPES}) hay không.
     *
     * <p>Nạp link của mỗi hồ sơ đúng MỘT lần rồi group theo type: hàm này nằm trong vòng lặp của
     * {@code detectAndCreateCandidatesForProfile}, nếu query lại cho từng type thì thành 5 query mỗi
     * hồ sơ (10 mỗi cặp) trong khi tất cả đều là cùng một câu truy vấn.
     */
    private boolean matchedByUniqueTypedId(MasterProfile left, MasterProfile right) {
        Map<String, Set<String>> leftByType = activeTypedIdentityValues(left.getId());
        if (leftByType.isEmpty()) {
            return false;
        }
        Map<String, Set<String>> rightByType = activeTypedIdentityValues(right.getId());
        for (Map.Entry<String, Set<String>> entry : leftByType.entrySet()) {
            Set<String> rightValues = rightByType.get(entry.getKey());
            if (rightValues == null) {
                continue;
            }
            if (entry.getValue().stream().anyMatch(rightValues::contains)) {
                return true;
            }
        }
        return false;
    }

    /** Giá trị các khóa typed đang ACTIVE của một hồ sơ, nhóm theo identityType. */
    private Map<String, Set<String>> activeTypedIdentityValues(Long masterProfileId) {
        Map<String, Set<String>> byType = new HashMap<>();
        for (ProfileIdentityLink link : identityLinkRepository
                .findByMasterProfileIdAndStatus(masterProfileId, (short) 1)) {
            if (!UNIQUE_TYPED_IDENTITY_TYPES.contains(link.getIdentityType())
                    || !StringUtils.hasText(link.getIdentityValue())) {
                continue;
            }
            byType.computeIfAbsent(link.getIdentityType(), k -> new HashSet<>())
                    .add(link.getIdentityValue());
        }
        return byType;
    }

    /**
     * Thêm id của các hồ sơ tìm được vào pool, bỏ chính hồ sơ đang xét.
     *
     * <p>Nếu một giá trị khóa khớp quá nhiều hồ sơ thì nó không còn tính phân biệt — thực tế là SĐT
     * rác / hotline shipper / "0000000000". Ghép cặp với tất cả sẽ sinh hàng loạt candidate nhiễu và
     * làm ngập màn đối soát, nên bỏ qua khóa đó và ghi log để đội dữ liệu biết mà xử lý.
     */
    private void addToPool(List<MasterProfile> found, Long selfId, Set<Long> pool, String keyLabel) {
        if (found.size() > MAX_PROFILES_PER_KEY) {
            log.warn("ProfileMatchCandidateServiceImpl - {} hồ sơ cùng {} (> {}) → khóa không có tính "
                            + "phân biệt, bỏ qua để tránh sinh candidate hàng loạt. profileId={}",
                    found.size(), keyLabel, MAX_PROFILES_PER_KEY, selfId);
            return;
        }
        for (MasterProfile p : found) {
            if (!p.getId().equals(selfId)) {
                pool.add(p.getId());
            }
        }
    }

    /**
     * Nâng kết quả probabilistic thành kết quả deterministic: ghi đè score / matchLevel /
     * autoMergeRecommended, giữ nguyên reasons và cờ identityConflict.
     */
    private ProfileMatchScoreResult promoteDeterministic(ProfileMatchScoreResult scoreResult,
                                                        BigDecimal deterministicScore,
                                                        String rule,
                                                        MasterProfile left, MasterProfile right) {
        scoreResult.setScore(deterministicScore.setScale(2, RoundingMode.HALF_UP));
        scoreResult.setMatchLevel(resolveMatchLevel(scoreResult.getScore()));
        // Khóa mạnh CÒN LẠI vẫn có thể lệch (trùng CCCD nhưng khác MST). Giữ nguyên cờ
        // identityConflict của scorer và không đề xuất auto-merge trong trường hợp đó — thận trọng
        // hơn ingestion một bậc, vì merge chưa có cơ chế hoàn tác.
        scoreResult.setAutoMergeRecommended(!scoreResult.isIdentityConflict());
        log.info("ProfileMatchCandidateServiceImpl - resolveMatch pair ({},{}): deterministic {} "
                        + "→ score={}, level={}, autoMerge={}, conflict={}",
                left.getId(), right.getId(), rule, scoreResult.getScore(), scoreResult.getMatchLevel(),
                scoreResult.isAutoMergeRecommended(), scoreResult.isIdentityConflict());
        return scoreResult;
    }

    /**
     * Tên hai bên "không lệch quá xa" theo đúng ngưỡng {@link #NAME_SIMILARITY_MIN} đang dùng ở
     * scorer và ingestion. Thiếu tên ở một bên thì coi như đạt — không có cơ sở để phủ định khóa mạnh.
     */
    private boolean nameCompatible(String leftName, String rightName) {
        String l = IdentityUtils.normalizeName(leftName);
        String r = IdentityUtils.normalizeName(rightName);
        if (!StringUtils.hasText(l) || !StringUtils.hasText(r)) {
            return true;
        }
        return IdentityUtils.calculateNameSimilarity(l, r) >= NAME_SIMILARITY_MIN;
    }

    /**
     * Core persist: saves ProfileMatchCandidate + ProfileMatchReason rows.
     */
    private ProfileMatchCandidate persistCandidateWithReasons(MasterProfile left, MasterProfile right,
                                                               ProfileMatchScoreResult scoreResult) {
        ProfileIdentityLink leftLink  = getPrimaryLink(left.getId());
        ProfileIdentityLink rightLink = getPrimaryLink(right.getId());

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
        candidate = candidateRepository.save(candidate);

        final Long candidateId = candidate.getId();
        LocalDateTime now = LocalDateTime.now();
        List<ProfileMatchReason> reasons = scoreResult.getReasons().stream().map(item -> {
            ProfileMatchReason r = new ProfileMatchReason();
            r.setMatchCandidateId(candidateId);
            r.setReasonType(item.getReasonType());
            r.setReasonMessage(item.getReasonMessage());
            r.setLeftValue(item.getLeftValue());
            r.setRightValue(item.getRightValue());
            r.setScore(item.getScore());
            return r;
        }).collect(Collectors.toList());
        reasonRepository.saveAll(reasons);
        return candidate;
    }

    private ProfileIdentityLink getPrimaryLink(Long masterProfileId) {
        List<ProfileIdentityLink> links = identityLinkRepository.findByMasterProfileId(masterProfileId);
        return links.stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsPrimary()) && l.getStatus() == 1)
                .findFirst()
                .orElseGet(() -> links.stream()
                        .filter(l -> l.getStatus() == 1)
                        .findFirst()
                        .orElse(links.isEmpty() ? null : links.get(0)));
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

    private MasterProfile chooseTarget(MasterProfile left, MasterProfile right,
                                       ProfileMatchCandidate candidate) {
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

    private void copyIdentityLinks(Long sourceId, Long targetId, String actor, LocalDateTime now) {
        List<ProfileIdentityLink> sourceLinks = identityLinkRepository.findByMasterProfileId(sourceId);
        List<ProfileIdentityLink> targetLinks = identityLinkRepository.findByMasterProfileId(targetId);

        for (ProfileIdentityLink sl : sourceLinks) {
            boolean exists = targetLinks.stream().anyMatch(tl ->
                    Objects.equals(tl.getSourceSystem(), sl.getSourceSystem())
                    && Objects.equals(tl.getSourceCustomerId(), sl.getSourceCustomerId()));
            if (!exists) {
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
                identityLinkRepository.save(newLink);
            }
            sl.setStatus((short) 3); // MERGED
            identityLinkRepository.save(sl);
        }
    }

    private void copyAttributeValues(Long sourceId, Long targetId) {
        List<ProfileAttributeValue> sourceValues = attributeValueRepository.findByMasterProfileId(sourceId);
        List<ProfileAttributeValue> targetValues = attributeValueRepository.findByMasterProfileId(targetId);

        for (ProfileAttributeValue sv : sourceValues) {
            boolean exists = targetValues.stream().anyMatch(tv ->
                    Objects.equals(tv.getSourceSystem(), sv.getSourceSystem())
                    && Objects.equals(tv.getPropertyName(), sv.getPropertyName())
                    && Objects.equals(tv.getPropertyValue(), sv.getPropertyValue()));
            if (!exists) {
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
                attributeValueRepository.save(nv);
            }
        }
    }

    /**
     * Chuyển các bảng tham chiếu tới source sang target: customer_events, profile_source_records,
     * profile_merge_conflicts. Dùng bulk update (không load entity) vì số dòng event có thể lớn.
     */
    private void reassignReferencedData(Long sourceId, Long targetId) {
        int events = customerEventRepository.reassignMasterProfile(sourceId, targetId);
        int records = sourceRecordRepository.reassignMasterProfile(sourceId, targetId);
        int conflicts = conflictRepository.reassignMasterProfile(sourceId, targetId);
        log.info("ProfileMatchCandidateServiceImpl - re-pointed source={} -> target={}: "
                        + "events={}, sourceRecords={}, conflicts={}",
                sourceId, targetId, events, records, conflicts);
    }

    /**
     * Đánh EXPIRED cho các candidate PENDING khác còn tham chiếu tới hồ sơ vừa bị merge.
     * Không re-point sang target vì matchScore của chúng được tính trên dữ liệu của source
     * (đã lỗi thời) và có thể trùng với candidate PENDING đã tồn tại của target. Cặp trùng thật
     * sẽ được {@link #detectAndCreateCandidatesForProfile(Long)} tạo lại với score tính đúng —
     * trạng thái EXPIRED không chặn việc tạo lại (khác IGNORED/REJECTED).
     */
    private void expireStaleCandidatesForMergedProfile(Long mergedProfileId, Long currentCandidateId,
                                                       String actor, LocalDateTime now) {
        List<ProfileMatchCandidate> stale = candidateRepository
                .findByProfileIdAndStatus(mergedProfileId, STATUS_PENDING)
                .stream()
                .filter(c -> !c.getId().equals(currentCandidateId))
                .collect(Collectors.toList());

        if (stale.isEmpty()) {
            return;
        }
        for (ProfileMatchCandidate c : stale) {
            c.setStatus(STATUS_EXPIRED);
            c.setDecisionBy(actor);
            c.setDecisionAt(now);
        }
        candidateRepository.saveAll(stale);
        log.info("ProfileMatchCandidateServiceImpl - expired {} stale PENDING candidate(s) referencing "
                + "merged profile {}", stale.size(), mergedProfileId);
    }

    private void syncToUnomi(MasterProfile profile, String syncType) {
        ProfileUnomiSyncLog syncLog = new ProfileUnomiSyncLog();
        syncLog.setMasterProfileId(profile.getId());
        syncLog.setProfileCode(profile.getProfileCode());
        syncLog.setSyncType(syncType);
        syncLog.setCreatedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        try {
            Object result = unomiService.syncProfileToUnomi(profile).block();
            syncLog.setStatus((short) 1);
            syncLog.setResponsePayload(result != null ? objectMapper.convertValue(result, Map.class) : null);
            syncLog.setSyncedAt(LocalDateTime.now());
            profile.setSyncedToUnomiAt(LocalDateTime.now());
            masterProfileRepository.save(profile);
        } catch (Exception ex) {
            syncLog.setStatus((short) 2);
            syncLog.setErrorMessage(ex.getMessage());
            syncLog.setSyncedAt(LocalDateTime.now());
            log.error("ProfileMatchCandidateServiceImpl - Unomi sync FAILED: profileCode={}",
                    profile.getProfileCode(), ex);
        }
        unomiSyncLogRepository.save(syncLog);
    }

    private Optional<ProfileMatchCandidate> findExistingCandidate(Long leftId, Long rightId) {
        return candidateRepository
                .findTopByLeftMasterProfileIdAndRightMasterProfileIdOrderByCreatedDesc(leftId, rightId)
                .or(() -> candidateRepository
                        .findTopByRightMasterProfileIdAndLeftMasterProfileIdOrderByCreatedDesc(leftId, rightId));
    }

    private ProfileMatchCandidate loadCandidate(Long id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Match candidate not found: " + id));
    }

    private MasterProfile loadProfile(Long id) {
        return masterProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Master profile not found: " + id));
    }

    private MasterProfile loadActiveProfile(Long id) {
        MasterProfile p = loadProfile(id);
        if (p.getStatus() != null
                && (p.getStatus() == PROFILE_MERGED || p.getStatus() == PROFILE_DELETED)) {
            throw new BusinessException("INVALID_PROFILE",
                    "Profile " + id + " is MERGED or DELETED and cannot be used for matching");
        }
        return p;
    }

    private void validatePending(ProfileMatchCandidate candidate) {
        if (candidate.getStatus() != STATUS_PENDING) {
            throw new BusinessException("INVALID_STATUS",
                    "Candidate must be in PENDING status. Current: " + candidate.getStatus());
        }
    }

    // =====================================================================
    // RESPONSE MAPPING
    // =====================================================================

    private ProfileMatchCandidateResponse toResponseWithLookup(ProfileMatchCandidate candidate) {
        MasterProfile left  = masterProfileRepository.findById(candidate.getLeftMasterProfileId()).orElse(null);
        MasterProfile right = masterProfileRepository.findById(candidate.getRightMasterProfileId()).orElse(null);
        List<ProfileMatchReason> reasons = reasonRepository.findByMatchCandidateId(candidate.getId());
        return toResponse(candidate, left, right, reasons);
    }

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
                .taxCode(p.getTaxCode())
                .dateOfBirth(p.getDateOfBirth())
                .gender(p.getGender())
                .customerType(p.getCustomerType())
                .customerTypeText(CustomerType.textOf(p.getCustomerType()))
                .provinceCode(p.getProvinceCode())
                .provinceName(p.getProvinceName())
                .unitCode(p.getUnitCode())
                .unitName(p.getUnitName())
                .profileStatus(p.getStatus())
                .profileStatusText(profileStatusText(p.getStatus()))
                .build();
    }

    private ProfileMatchReasonResponse toReasonResponse(ProfileMatchReason r) {
        return ProfileMatchReasonResponse.builder()
                .id(r.getId())
                .reasonType(r.getReasonType())
                .reasonTypeText(reasonTypeText(r.getReasonType()))
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

    /** Trạng thái của master profile — khác bảng mã status của candidate ở {@link #statusText}. */
    private String profileStatusText(Short s) {
        if (s == null) return "";
        return switch (s) {
            case 1  -> "Đang hoạt động";
            case 2  -> "Ngừng hoạt động";
            case 3  -> "Đã gộp";
            case 4  -> "Bị khoá";
            case 5  -> "Đã xoá";
            default -> String.valueOf(s);
        };
    }

    /** Nhãn hiển thị của khoá khớp trên UI. */
    private String reasonTypeText(String reasonType) {
        if (reasonType == null) return "";
        return switch (reasonType) {
            case "IDENTITY_NO_MATCH"   -> "CCCD/CMND";
            case "IDENTITY_CONFLICT"   -> "CCCD/CMND lệch";
            case "TAX_CODE_MATCH"      -> "MST";
            case "TAX_CODE_CONFLICT"   -> "MST lệch";
            case "PHONE_MATCH"         -> "SĐT";
            case "PHONE_CONFLICT"      -> "SĐT lệch";
            case "EMAIL_MATCH"         -> "Email";
            case "EMAIL_CONFLICT"      -> "Email lệch";
            case "NAME_EXACT_MATCH"    -> "Tên trùng khớp";
            case "NAME_SIMILAR"        -> "Tên gần đúng";
            case "DATE_OF_BIRTH_MATCH" -> "Ngày sinh";
            case "PROVINCE_MATCH"      -> "Tỉnh/TP";
            case "UNIT_MATCH"          -> "Bưu cục";
            default                    -> reasonType;
        };
    }

    /** Cùng thang với {@code ProfileMatchScoreService.resolveMatchLevel} để badge trên 2 màn khớp nhau. */
    private String resolveMatchLevel(BigDecimal score) {
        if (score == null) return "LOW";
        if (score.compareTo(BigDecimal.valueOf(IdentityMatchThresholds.LEVEL_VERY_HIGH)) >= 0) return "VERY_HIGH";
        if (score.compareTo(BigDecimal.valueOf(IdentityMatchThresholds.LEVEL_HIGH)) >= 0) return "HIGH";
        if (score.compareTo(BigDecimal.valueOf(IdentityMatchThresholds.LEVEL_MEDIUM)) >= 0) return "MEDIUM";
        return "LOW";
    }

    // ---- Ép kiểu cột của native query (JDBC driver có thể trả Integer/BigInteger/Long) ----

    private Long asLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private BigDecimal asBigDecimal(Object v) {
        if (v == null) return null;
        return v instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) v).doubleValue());
    }

    private String asString(Object v) {
        return v == null ? null : v.toString();
    }

}

