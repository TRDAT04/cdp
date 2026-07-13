package vn.vnpost.cdp.profile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.profile.assembler.ProfileListAssembler;
import vn.vnpost.cdp.profile.dto.query.*;
import vn.vnpost.cdp.profile.entity.*;
import vn.vnpost.cdp.profile.repository.*;
import vn.vnpost.cdp.unomi.client.UnomiClient;
import vn.vnpost.cdp.unomi.dto.UnomiProfileResponse;
import vn.vnpost.cdp.unomi.dto.UnomiProfileSearchResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProfileQueryServiceImpl implements ProfileQueryService {

    private final MasterProfileRepository masterProfileRepository;
    private final ProfileIdentityLinkRepository identityLinkRepository;
    private final ProfileAttributeValueRepository attributeValueRepository;
    private final ProfileSourceRecordRepository sourceRecordRepository;
    private final ProfileMergeConflictRepository conflictRepository;
    private final ProfileMatchCandidateRepository matchCandidateRepository;
    private final ProfileChangeLogRepository changeLogRepository;
    private final ProfileUnomiSyncLogRepository unomiSyncLogRepository;
    private final UnomiClient unomiClient;
    private final ProfileListAssembler profileListAssembler;
    private final vn.vnpost.cdp.profile.assembler.ProfileDetailAssembler profileDetailAssembler;

    public ProfileQueryServiceImpl(
            MasterProfileRepository masterProfileRepository,
            ProfileIdentityLinkRepository identityLinkRepository,
            ProfileAttributeValueRepository attributeValueRepository,
            ProfileSourceRecordRepository sourceRecordRepository,
            ProfileMergeConflictRepository conflictRepository,
            ProfileMatchCandidateRepository matchCandidateRepository,
            ProfileChangeLogRepository changeLogRepository,
            ProfileUnomiSyncLogRepository unomiSyncLogRepository,
            UnomiClient unomiClient,
            ProfileListAssembler profileListAssembler,
            vn.vnpost.cdp.profile.assembler.ProfileDetailAssembler profileDetailAssembler) {
        this.masterProfileRepository = masterProfileRepository;
        this.identityLinkRepository = identityLinkRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.sourceRecordRepository = sourceRecordRepository;
        this.conflictRepository = conflictRepository;
        this.matchCandidateRepository = matchCandidateRepository;
        this.changeLogRepository = changeLogRepository;
        this.unomiSyncLogRepository = unomiSyncLogRepository;
        this.unomiClient = unomiClient;
        this.profileListAssembler = profileListAssembler;
        this.profileDetailAssembler = profileDetailAssembler;
    }

    // =====================================================================
    // DETAIL
    // =====================================================================

    @Override
    public ProfileDetailResponse getProfileDetail(Long id) {
        MasterProfile profile = masterProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Profile not found: " + id));
        Long profileId = profile.getId();

        List<ProfileIdentityLink> links = identityLinkRepository.findByMasterProfileId(profileId);
        List<ProfileAttributeValue> attrs = attributeValueRepository.findByMasterProfileId(profileId);
        List<ProfileSourceRecord> records = sourceRecordRepository.findByMasterProfileIdOrderByReceivedAtDesc(profileId);
        List<ProfileMergeConflict> conflicts = conflictRepository.findByMasterProfileIdAndResolutionStatus(profileId, (short) 0);
        List<ProfileMatchCandidate> candidates = matchCandidateRepository
                .findByLeftMasterProfileIdOrRightMasterProfileId(profileId, profileId)
                .stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 0)
                .collect(Collectors.toList());
        List<ProfileChangeLog> logs = changeLogRepository.findTop20ByMasterProfileIdOrderByChangedAtDesc(profileId);
        ProfileUnomiSyncLog latestSync = unomiSyncLogRepository.findTopByMasterProfileIdOrderBySyncedAtDesc(profileId).orElse(null);

        List<String> sourceSystems = resolveSourceSystems(profileId, links);
        String[] warning = resolveWarning(profileId);
        LocalDateTime lastActivity = resolveLastActivity(profileId, profile, attrs);

        UnomiProfileResponse unomiData = null;
        if (StringUtils.hasText(profile.getProfileCode())) {
            UnomiProfileSearchResponse unomiResponse = unomiClient
                    .searchProfilesByCodes(List.of(profile.getProfileCode()))
                    .block();
            if (unomiResponse != null && !org.springframework.util.CollectionUtils.isEmpty(unomiResponse.getList())) {
                unomiData = unomiResponse.getList().get(0);
            }
        }

        return profileDetailAssembler.assemble(
                profile, unomiData, warning, sourceSystems, lastActivity,
                links, attrs, records, conflicts, candidates, logs, latestSync
        );
    }

    // =====================================================================
    // LIST / SEARCH
    // =====================================================================

    @Override
    public Page<ProfileListItemResponse> searchProfiles(ProfileSearchRequest request, Pageable pageable) {
        Specification<MasterProfile> spec = buildSpec(request);
        Page<MasterProfile> profilePage = masterProfileRepository.findAll(spec, pageable);

        List<String> profileCodes = profilePage.getContent().stream()
                .map(MasterProfile::getProfileCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        UnomiProfileSearchResponse unomiResponse = unomiClient
                .searchProfilesByCodes(profileCodes)
                .block();

        Map<String, UnomiProfileResponse> unomiIndex =
                profileListAssembler.buildUnomiIndex(unomiResponse.getList());

        final Map<String, UnomiProfileResponse> finalIndex = unomiIndex;
        return profilePage.map(profile -> {
            List<ProfileIdentityLink> links = identityLinkRepository
                    .findByMasterProfileIdAndStatus(profile.getId(), (short) 1);
            List<String> sourceSystems = resolveSourceSystems(profile.getId(), links);
            String[] warning = resolveWarning(profile.getId());
            LocalDateTime lastActivityAt = resolveLastActivity(profile.getId(), profile, null);
            UnomiProfileResponse unomiData = finalIndex.get(profile.getProfileCode());

            return profileListAssembler.assemble(profile, unomiData, warning, sourceSystems, lastActivityAt);
        });
    }

    private Specification<MasterProfile> buildSpec(ProfileSearchRequest request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            // Default: only ACTIVE profiles (status=1) unless explicitly filtered
            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            } else {
                predicates.add(cb.equal(root.get("status"), (short) 1));
            }

            if (StringUtils.hasText(request.getKeyword())) {
                String kw = "%" + request.getKeyword().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), kw),
                        cb.like(cb.lower(root.get("profileCode")), kw),
                        cb.like(cb.lower(root.get("phone")), kw),
                        cb.like(cb.lower(root.get("email")), kw)
                ));
            }

            if (StringUtils.hasText(request.getCustomerType())) {
                predicates.add(cb.equal(root.get("customerType"), request.getCustomerType()));
            }

            if (StringUtils.hasText(request.getSourceSystem())) {
                // Subquery: profile must have an active identity link for this source system
                var sub = query.subquery(Long.class);
                var linkRoot = sub.from(ProfileIdentityLink.class);
                sub.select(linkRoot.get("masterProfileId"))
                        .where(cb.and(
                                cb.equal(linkRoot.get("sourceSystem"), request.getSourceSystem()),
                                cb.equal(linkRoot.get("status"), (short) 1)
                        ));
                predicates.add(root.get("id").in(sub));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }


    private List<String> resolveSourceSystems(Long profileId, List<ProfileIdentityLink> links) {
        if (links == null) {
            links = identityLinkRepository.findByMasterProfileIdAndStatus(profileId, (short) 1);
        }
        return links.stream()
                .map(ProfileIdentityLink::getSourceSystem)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    // =====================================================================
    // HELPER: resolveWarning  returns String[2] {warningStatus, warningText}
    // =====================================================================

    private String[] resolveWarning(Long profileId) {
        boolean hasConflict = conflictRepository.existsByMasterProfileIdAndResolutionStatus(profileId, (short) 0);
        if (hasConflict) {
            return new String[]{"CONFLICT", "Có xung đột"};
        }
        boolean hasPending = matchCandidateRepository.existsPendingCandidateForProfile(profileId, (short) 0);
        if (hasPending) {
            return new String[]{"NEED_REVIEW", "Cần rà soát"};
        }
        return new String[]{"NORMAL", "Bình thường"};
    }

    // =====================================================================
    // HELPER: resolveLastActivity
    // =====================================================================

    private LocalDateTime resolveLastActivity(Long profileId, MasterProfile profile,
                                               List<ProfileAttributeValue> attrs) {
        var activityAttr = attributeValueRepository
                .findTopByMasterProfileIdAndPropertyNameInOrderByReceivedAtDesc(
                        profileId, List.of("lastVisitAt", "lastActivityAt"))
                .orElse(null);
        if (activityAttr != null) {
            return activityAttr.getReceivedAt();
        }
        if (profile.getModified() != null) return profile.getModified();
        return profile.getCreated();
    }




}

