package vn.vnpost.cdp.profile.service.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.profile.dto.match.*;
import vn.vnpost.cdp.profile.entity.*;
import vn.vnpost.cdp.profile.repository.*;
import vn.vnpost.cdp.security.SecurityUtils;
import vn.vnpost.cdp.unomi.service.UnomiService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ProfileMatchCandidateServiceImpl implements ProfileMatchCandidateService {

    private static final short STATUS_PENDING  = 0;
    private static final short STATUS_MERGED   = 1;
    private static final short STATUS_IGNORED  = 2;
    private static final short STATUS_REJECTED = 3;
    private static final short STATUS_EXPIRED  = 4;

    private static final short PROFILE_MERGED  = 3;
    private static final short PROFILE_DELETED = 5;

    private static final List<String> SOURCE_PRIORITY = List.of("CRM", "MYVNPOST", "PORTAL", "CMS");

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
            ObjectMapper objectMapper) {
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
    }

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

        ProfileMatchScoreResult scoreResult = scoreService.calculate(left, right);
        BigDecimal newScore = scoreResult.getScore();

        if (newScore.compareTo(BigDecimal.valueOf(70)) < 0) {
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
                if (diff.compareTo(BigDecimal.TEN) < 0) {
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
            query.orderBy(cb.desc(root.get("matchScore")), cb.desc(root.get("created")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return candidateRepository.findAll(spec).stream()
                .map(this::toResponseWithLookup)
                .collect(Collectors.toList());
    }

    // =====================================================================
    // LIST
    // =====================================================================

    @Override
    @Transactional(readOnly = true)
    public List<ProfileMatchCandidateResponse> listPending() {
        return candidateRepository.findByStatusOrderByMatchScoreDescCreatedDesc(STATUS_PENDING)
                .stream().map(this::toResponseWithLookup).collect(Collectors.toList());
    }

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

        MasterProfile left  = loadProfile(candidate.getLeftMasterProfileId());
        MasterProfile right = loadProfile(candidate.getRightMasterProfileId());

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

        // 13. Sync target to Unomi
        syncToUnomi(target, "MERGE");

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
            masterProfileRepository.findByIdentityNo(profile.getIdentityNo())
                    .filter(p -> !p.getId().equals(masterProfileId))
                    .ifPresent(p -> candidateProfileIds.add(p.getId()));
        }
        if (StringUtils.hasText(profile.getPhone())) {
            masterProfileRepository.findByPhone(profile.getPhone())
                    .filter(p -> !p.getId().equals(masterProfileId))
                    .ifPresent(p -> candidateProfileIds.add(p.getId()));
        }
        if (StringUtils.hasText(profile.getEmail())) {
            masterProfileRepository.findByEmail(profile.getEmail())
                    .filter(p -> !p.getId().equals(masterProfileId))
                    .ifPresent(p -> candidateProfileIds.add(p.getId()));
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

                ProfileMatchScoreResult scoreResult = scoreService.calculate(profile, candidateProfile);
                if (scoreResult.getScore().compareTo(BigDecimal.valueOf(70)) < 0) {
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
                        if (diff.compareTo(BigDecimal.TEN) < 0) {
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

