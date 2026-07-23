package vn.vnpost.cdp.profile.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.customer_event.entity.CustomerEvent;
import vn.vnpost.cdp.customer_event.repository.CustomerEventRepository;
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
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileQueryServiceImpl implements ProfileQueryService {

    /**
     * Giới hạn số member kéo về từ Unomi khi lọc theo segment. Segment lớn hơn ngưỡng này
     * sẽ bị cắt bớt (log.warn), kết quả lọc có thể sót — cân nhắc lưu segment vào DB nếu gặp.
     */
    private static final int SEGMENT_MEMBER_FETCH_LIMIT = 5000;

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
    private final CustomerEventRepository customerEventRepository;



    // =====================================================================
    // DETAIL
    // =====================================================================

    @Override
    public ProfileDetailResponse getProfileDetail(Long id) {
        MasterProfile profile = findProfileOrThrow(id);
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

        UnomiProfileResponse unomiData = fetchUnomiData(profile);

        return profileDetailAssembler.assemble(
                profile, unomiData, warning, sourceSystems, lastActivity,
                links, attrs, records, conflicts, candidates, logs, latestSync
        );
    }

    // =====================================================================
    // DETAIL - TÁCH THEO TAB
    // =====================================================================

    @Override
    public ProfileOverviewResponse getProfileOverview(Long id) {
        MasterProfile profile = findProfileOrThrow(id);
        List<ProfileIdentityLink> links = identityLinkRepository.findByMasterProfileId(profile.getId());
        UnomiProfileResponse unomiData = fetchUnomiData(profile);
        return profileDetailAssembler.assembleOverview(profile, unomiData, links);
    }

    @Override
    public List<ProfileIdentityLinkDetailResponse> getProfileIdentityLinks(Long id) {
        MasterProfile profile = findProfileOrThrow(id);
        List<ProfileIdentityLink> links = identityLinkRepository.findByMasterProfileId(profile.getId());
        return profileDetailAssembler.toIdentityLinkResponses(links);
    }

    @Override
    public ProfileMultiSourceComparisonResponse getProfileMultiSource(Long id) {
        MasterProfile profile = findProfileOrThrow(id);
        List<ProfileAttributeValue> attrs = attributeValueRepository.findByMasterProfileId(profile.getId());
        List<ProfileIdentityLink> links = identityLinkRepository.findByMasterProfileId(profile.getId());
        List<ProfileSourceRecord> records = sourceRecordRepository.findByMasterProfileId(profile.getId());
        return profileDetailAssembler.assembleMultiSource(profile, attrs, links, records);
    }

    @Override
    public ProfileAddressResponse getProfileAddress(Long id) {
        MasterProfile profile = findProfileOrThrow(id);
        return profileDetailAssembler.assembleAddress(profile);
    }

    @Override
    public ProfileDigitalBehaviorResponse getProfileBehavior(Long id) {
        MasterProfile profile = findProfileOrThrow(id);
        List<CustomerEvent> events = customerEventRepository
                .findTop50ByMasterProfileIdOrderByOccurredAtDesc(profile.getId());
        return profileDetailAssembler.assembleBehavior(events);
    }

    @Override
    public ProfileChangeLogsResponse getProfileChangeLogs(Long id) {
        MasterProfile profile = findProfileOrThrow(id);
        Long profileId = profile.getId();
        List<ProfileChangeLog> logs = changeLogRepository.findTop20ByMasterProfileIdOrderByChangedAtDesc(profileId);
        ProfileUnomiSyncLog latestSync = unomiSyncLogRepository
                .findTopByMasterProfileIdOrderBySyncedAtDesc(profileId).orElse(null);
        return profileDetailAssembler.assembleChangeLogs(logs, latestSync);
    }

    @Override
    public ProfileServiceLinesResponse getProfileServiceLines(Long id) {
        MasterProfile profile = findProfileOrThrow(id);
        List<CustomerEvent> events = customerEventRepository
                .findTop50ByMasterProfileIdOrderByOccurredAtDesc(profile.getId());
        return profileDetailAssembler.assembleServiceLines(profile.getId(), events);
    }

    @Override
    public ProfileCskhResponse getProfileCskh(Long id) {
        MasterProfile profile = findProfileOrThrow(id);
        List<CustomerEvent> events = customerEventRepository
                .findTop50ByMasterProfileIdOrderByOccurredAtDesc(profile.getId());
        return profileDetailAssembler.assembleCskh(events);
    }

    @Override
    public vn.vnpost.cdp.profile.dto.query.ProfileSummaryResponse getProfileSummary(Long id) {
        MasterProfile profile = findProfileOrThrow(id);
        List<ProfileIdentityLink> links = identityLinkRepository.findByMasterProfileIdAndStatus(profile.getId(), (short) 1);
        UnomiProfileResponse unomiData = fetchUnomiData(profile);
        List<CustomerEvent> events = customerEventRepository
                .findTop50ByMasterProfileIdOrderByOccurredAtDesc(profile.getId());
        return profileDetailAssembler.assembleSummary(profile, unomiData, links, events);
    }

    // =====================================================================
    // HELPER: dùng chung cho detail + các tab
    // =====================================================================

    private MasterProfile findProfileOrThrow(Long id) {
        return masterProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Profile not found: " + id));
    }

    private UnomiProfileResponse fetchUnomiData(MasterProfile profile) {
        if (!StringUtils.hasText(profile.getProfileCode())) {
            return null;
        }
        UnomiProfileSearchResponse unomiResponse = unomiClient
                .searchProfilesByCodes(List.of(profile.getProfileCode()))
                .block();
        if (unomiResponse != null && !org.springframework.util.CollectionUtils.isEmpty(unomiResponse.getList())) {
            return unomiResponse.getList().get(0);
        }
        return null;
    }

    // =====================================================================
    // LIST / SEARCH
    // =====================================================================

    @Override
    public Page<ProfileListItemResponse> searchProfiles(ProfileSearchRequest request, Pageable pageable) {
        // Segment sống ở Unomi (không có trong DB): quy về danh sách profileCode để DB giao với các filter khác.
        // null  = không lọc segment; empty = có lọc nhưng segment không có thành viên -> trả 0 kết quả.
        List<String> segmentProfileCodes = resolveSegmentProfileCodes(request.getSegment());

        Specification<MasterProfile> spec = buildSpec(request, segmentProfileCodes);
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

        // Batch fetch customer_events cho toàn bộ trang trong một query, group theo masterProfileId
        // (tránh N+1). Mỗi list vẫn giữ thứ tự occurredAt DESC do repository đã sort.
        List<Long> profileIds = profilePage.getContent().stream()
                .map(MasterProfile::getId)
                .collect(Collectors.toList());
        Map<Long, List<CustomerEvent>> eventsByProfileId = profileIds.isEmpty()
                ? Collections.emptyMap()
                : customerEventRepository.findByMasterProfileIdInOrderByOccurredAtDesc(profileIds).stream()
                        .filter(e -> e.getMasterProfileId() != null)
                        .collect(Collectors.groupingBy(CustomerEvent::getMasterProfileId));

        final Map<String, UnomiProfileResponse> finalIndex = unomiIndex;
        return profilePage.map(profile -> {
            List<ProfileIdentityLink> links = identityLinkRepository
                    .findByMasterProfileIdAndStatus(profile.getId(), (short) 1);
            List<String> sourceSystems = resolveSourceSystems(profile.getId(), links);
            String[] warning = resolveWarning(profile.getId());
            LocalDateTime lastActivityAt = resolveLastActivity(profile.getId(), profile, null);
            UnomiProfileResponse unomiData = finalIndex.get(profile.getProfileCode());
            List<CustomerEvent> events = eventsByProfileId
                    .getOrDefault(profile.getId(), Collections.emptyList());

            return profileListAssembler.assemble(profile, unomiData, warning, sourceSystems, lastActivityAt, events, links);
        });
    }

    private Specification<MasterProfile> buildSpec(ProfileSearchRequest request, List<String> segmentProfileCodes) {
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

            if (StringUtils.hasText(request.getCustomerGroup())) {
                predicates.add(cb.equal(root.get("customerGroup"), request.getCustomerGroup()));
            }

            // Lọc theo segment: segmentProfileCodes đã được resolve từ Unomi trước đó.
            // null  -> không áp dụng filter segment.
            // empty -> có filter nhưng không member nào -> match nothing (0 kết quả).
            if (segmentProfileCodes != null) {
                if (segmentProfileCodes.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("profileCode").in(segmentProfileCodes));
                }
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

    /**
     * Lấy danh sách profileCode thuộc một segment từ Unomi.
     *
     * @return {@code null} nếu không lọc segment; danh sách (có thể rỗng) profileCode nếu có lọc.
     */
    private List<String> resolveSegmentProfileCodes(String segment) {
        if (!StringUtils.hasText(segment)) {
            return null;
        }

        UnomiProfileSearchResponse response = unomiClient
                .getSegmentMembers(segment, SEGMENT_MEMBER_FETCH_LIMIT)
                .block();

        List<UnomiProfileResponse> members = (response == null || response.getList() == null)
                ? Collections.emptyList()
                : response.getList();

        if (members.size() >= SEGMENT_MEMBER_FETCH_LIMIT) {
            log.warn("Segment '{}' có số thành viên đạt/vượt ngưỡng {} — kết quả lọc có thể bị sót.",
                    segment, SEGMENT_MEMBER_FETCH_LIMIT);
        }

        List<String> codes = members.stream()
                .map(p -> p.getProperties() != null ? p.getProperties().getCdpProfileCode() : null)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());

        log.debug("resolveSegmentProfileCodes - segment={}, members={}, distinctCodes={}",
                segment, members.size(), codes.size());
        return codes;
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

