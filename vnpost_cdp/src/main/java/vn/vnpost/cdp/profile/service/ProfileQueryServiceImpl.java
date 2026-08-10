package vn.vnpost.cdp.profile.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.customer_event.entity.CustomerEvent;
import vn.vnpost.cdp.customer_event.repository.CustomerEventRepository;
import vn.vnpost.cdp.profile.assembler.ProfileDetailAssembler;
import vn.vnpost.cdp.profile.assembler.ProfileListAssembler;
import vn.vnpost.cdp.profile.dto.query.*;
import vn.vnpost.cdp.profile.entity.*;
import vn.vnpost.cdp.profile.repository.*;
import vn.vnpost.cdp.unomi.client.UnomiClient;
import vn.vnpost.cdp.unomi.dto.UnomiProfileResponse;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileQueryServiceImpl implements ProfileQueryService {

    /**
     * Giới hạn số member kéo về từ Unomi khi lọc theo segment. Segment lớn hơn ngưỡng này
     * sẽ bị cắt bớt (log.warn), kết quả lọc có thể sót — cân nhắc lưu segment vào DB nếu gặp.
     */
    private static final int SEGMENT_MEMBER_FETCH_LIMIT = 5000;

    /** Cột hợp lệ cho tham số {@code sort} của searchProfiles (chống SQL injection qua tên cột). */
    private static final Map<String, String> SORTABLE_COLUMNS = Map.ofEntries(
            Map.entry("id", "id"),
            Map.entry("fullName", "full_name"),
            Map.entry("profileCode", "profile_code"),
            Map.entry("phone", "phone"),
            Map.entry("email", "email"),
            Map.entry("customerType", "customer_type"),
            Map.entry("customerTier", "customer_tier"),
            Map.entry("customerGroup", "customer_group"),
            Map.entry("provinceCode", "province_code"),
            Map.entry("unitCode", "unit_code"),
            Map.entry("status", "status"),
            Map.entry("created", "created"),
            Map.entry("modified", "modified")
    );

    /**
     * WHERE cố định cho searchProfiles: mỗi filter tùy chọn được bọc dạng
     * {@code (:xxxActive = false OR <điều kiện thật>)} — SQL text không đổi theo request,
     * chỉ giá trị bind thay đổi. Đây là cách thay thế JPA {@code Specification} động khi
     * R2DBC không hỗ trợ Specification/subquery trong Criteria (theo hướng dẫn: tách query
     * riêng rồi ghép ở tầng service thay vì để ORM tự dựng subquery).
     */
    private static final String SEARCH_WHERE_SQL = """
            WHERE status = :status
              AND (:keywordActive = false OR full_name ILIKE :keywordPattern OR profile_code ILIKE :keywordPattern
                   OR phone ILIKE :keywordPattern OR email ILIKE :keywordPattern)
              AND (:customerTypeActive = false OR customer_type = :customerType)
              AND (:customerGroupActive = false OR customer_group = :customerGroup)
              AND (:segmentActive = false OR profile_code = ANY(:segmentCodes))
              AND (:sourceSystemActive = false OR id = ANY(:sourceSystemProfileIds))
            """;

    private final MasterProfileRepository masterProfileRepository;
    private final ProfileIdentityLinkRepository identityLinkRepository;
    private final ProfileAttributeValueRepository attributeValueRepository;
    private final ProfileSourceRecordRepository sourceRecordRepository;
    private final ProfileMergeConflictRepository conflictRepository;
    private final ProfileMatchCandidateRepository matchCandidateRepository;
    private final ProfileChangeLogRepository changeLogRepository;
    private final ProfileUnomiSyncLogRepository unomiSyncLogRepository;
    private final CustomerEventRepository customerEventRepository;
    private final UnomiClient unomiClient;
    private final ProfileListAssembler profileListAssembler;
    private final ProfileDetailAssembler profileDetailAssembler;
    private final R2dbcEntityTemplate entityTemplate;

    // =====================================================================
    // DETAIL - TÁCH THEO TAB
    // =====================================================================

    @Override
    public Mono<ProfileOverviewResponse> getProfileOverview(Long id) {
        return findProfileOrThrow(id).flatMap(profile -> {
            Mono<List<ProfileIdentityLink>> linksMono =
                    identityLinkRepository.findByMasterProfileId(profile.getId()).collectList();
            Mono<UnomiProfileResponse> unomiMono = fetchUnomiData(profile);
            return Mono.zip(linksMono, optional(unomiMono))
                    .map(t -> profileDetailAssembler.assembleOverview(profile, t.getT2().orElse(null), t.getT1()));
        });
    }

    @Override
    public Mono<List<ProfileIdentityLinkDetailResponse>> getProfileIdentityLinks(Long id) {
        return findProfileOrThrow(id)
                .flatMap(profile -> identityLinkRepository.findByMasterProfileId(profile.getId()).collectList())
                .map(profileDetailAssembler::toIdentityLinkResponses);
    }

    @Override
    public Mono<ProfileMultiSourceComparisonResponse> getProfileMultiSource(Long id) {
        return findProfileOrThrow(id).flatMap(profile -> {
            Mono<List<ProfileAttributeValue>> attrsMono =
                    attributeValueRepository.findByMasterProfileId(profile.getId()).collectList();
            Mono<List<ProfileIdentityLink>> linksMono =
                    identityLinkRepository.findByMasterProfileId(profile.getId()).collectList();
            Mono<List<ProfileSourceRecord>> recordsMono =
                    sourceRecordRepository.findByMasterProfileId(profile.getId()).collectList();
            return Mono.zip(attrsMono, linksMono, recordsMono)
                    .map(t -> profileDetailAssembler.assembleMultiSource(profile, t.getT1(), t.getT2(), t.getT3()));
        });
    }

    @Override
    public Mono<ProfileAddressResponse> getProfileAddress(Long id) {
        return findProfileOrThrow(id).map(profileDetailAssembler::assembleAddress);
    }

    @Override
    public Mono<ProfileDigitalBehaviorResponse> getProfileBehavior(Long id) {
        return findProfileOrThrow(id)
                .flatMap(profile -> customerEventRepository
                        .findTop50ByMasterProfileIdOrderByOccurredAtDesc(profile.getId())
                        .collectList())
                .map(profileDetailAssembler::assembleBehavior);
    }

    @Override
    public Mono<ProfileChangeLogsResponse> getProfileChangeLogs(Long id) {
        return findProfileOrThrow(id).flatMap(profile -> {
            Long profileId = profile.getId();
            Mono<List<ProfileChangeLog>> logsMono =
                    changeLogRepository.findTop20ByMasterProfileIdOrderByChangedAtDesc(profileId).collectList();
            // Toàn bộ lịch sử — dùng cho profileSummary (createdAt/createdBySystem cần dòng cũ nhất).
            Mono<List<ProfileChangeLog>> allLogsMono =
                    changeLogRepository.findByMasterProfileIdOrderByChangedAtDesc(profileId).collectList();
            Mono<ProfileUnomiSyncLog> latestSyncMono =
                    unomiSyncLogRepository.findTopByMasterProfileIdOrderBySyncedAtDesc(profileId);
            // sourceSystems: tái sử dụng đúng logic Overview/Detail (DISTINCT sourceSystem của hồ sơ).
            Mono<List<String>> sourceSystemsMono = resolveSourceSystems(profileId, null);

            return Mono.zip(logsMono, allLogsMono, optional(latestSyncMono), sourceSystemsMono)
                    .map(t -> profileDetailAssembler.assembleChangeLogs(
                            t.getT1(), t.getT2(), t.getT3().orElse(null), t.getT4()));
        });
    }

    @Override
    public Mono<ProfileServiceLinesResponse> getProfileServiceLines(Long id) {
        return findProfileOrThrow(id).flatMap(profile ->
                customerEventRepository.findTop50ByMasterProfileIdOrderByOccurredAtDesc(profile.getId())
                        .collectList()
                        .map(events -> profileDetailAssembler.assembleServiceLines(profile.getId(), events)));
    }

    @Override
    public Mono<ProfileCskhResponse> getProfileCskh(Long id) {
        return findProfileOrThrow(id)
                .flatMap(profile -> customerEventRepository
                        .findTop50ByMasterProfileIdOrderByOccurredAtDesc(profile.getId())
                        .collectList())
                .map(profileDetailAssembler::assembleCskh);
    }

    @Override
    public Mono<ProfileConsentResponse> getProfileConsent(Long id) {
        return findProfileOrThrow(id)
                // Lấy TOÀN BỘ event để không bỏ sót cặp (purpose, channel) cũ khi tính bản mới nhất.
                .flatMap(profile -> customerEventRepository
                        .findByMasterProfileIdOrderByOccurredAtDesc(profile.getId())
                        .collectList())
                .map(profileDetailAssembler::assembleConsent);
    }

    @Override
    public Mono<ProfileSummaryResponse> getProfileSummary(Long id) {
        return findProfileOrThrow(id).flatMap(profile -> {
            Mono<List<ProfileIdentityLink>> linksMono = identityLinkRepository
                    .findByMasterProfileIdAndStatus(profile.getId(), (short) 1).collectList();
            Mono<UnomiProfileResponse> unomiMono = fetchUnomiData(profile);
            Mono<List<CustomerEvent>> eventsMono = customerEventRepository
                    .findTop50ByMasterProfileIdOrderByOccurredAtDesc(profile.getId()).collectList();
            return Mono.zip(linksMono, optional(unomiMono), eventsMono)
                    .map(t -> profileDetailAssembler.assembleSummary(profile, t.getT2().orElse(null), t.getT1(), t.getT3()));
        });
    }

    // =====================================================================
    // HELPER: dùng chung cho detail + các tab
    // =====================================================================

    private Mono<MasterProfile> findProfileOrThrow(Long id) {
        return masterProfileRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("NOT_FOUND", "Profile not found: " + id)));
    }

    private Mono<UnomiProfileResponse> fetchUnomiData(MasterProfile profile) {
        if (!StringUtils.hasText(profile.getProfileCode())) {
            return Mono.empty();
        }
        return unomiClient.searchProfilesByCodes(List.of(profile.getProfileCode()))
                .flatMap(unomiResponse -> {
                    if (unomiResponse != null && !org.springframework.util.CollectionUtils.isEmpty(unomiResponse.getList())) {
                        return Mono.just(unomiResponse.getList().get(0));
                    }
                    return Mono.empty();
                });
    }

    /** Bọc {@code Mono<T>} có thể rỗng thành {@code Mono<Optional<T>>} luôn có giá trị — an toàn khi ghép Mono.zip. */
    private static <T> Mono<Optional<T>> optional(Mono<T> mono) {
        return mono.map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    // =====================================================================
    // LIST / SEARCH
    // =====================================================================

    @Override
    public Mono<Page<ProfileListItemResponse>> searchProfiles(ProfileSearchRequest request, Pageable pageable) {
        // Segment sống ở Unomi (không có trong DB): quy về danh sách profileCode để DB giao với các filter khác.
        // null  = không lọc segment; empty = có lọc nhưng segment không có thành viên -> trả 0 kết quả.
        return Mono.zip(
                resolveSegmentProfileCodes(request.getSegment()),
                resolveSourceSystemProfileIds(request.getSourceSystem())
        ).flatMap(t -> runSearch(request, pageable, t.getT1().orElse(null), t.getT2().orElse(null)));
    }

    private Mono<Page<ProfileListItemResponse>> runSearch(ProfileSearchRequest request, Pageable pageable,
                                                            List<String> segmentProfileCodes,
                                                            List<Long> sourceSystemProfileIds) {
        String sortColumn = resolveSortColumn(pageable);
        String direction = resolveSortDirection(pageable);

        String dataSql = "SELECT * FROM master_profiles " + SEARCH_WHERE_SQL
                + "ORDER BY " + sortColumn + " " + direction + " LIMIT :limit OFFSET :offset";
        String countSql = "SELECT COUNT(*) FROM master_profiles " + SEARCH_WHERE_SQL;

        Mono<List<MasterProfile>> profilesMono = bindSearchParams(
                        entityTemplate.getDatabaseClient().sql(dataSql), request, segmentProfileCodes, sourceSystemProfileIds)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map((row, metadata) -> entityTemplate.getConverter().read(MasterProfile.class, row, metadata))
                .all()
                .collectList();

        Mono<Long> totalMono = bindSearchParams(
                        entityTemplate.getDatabaseClient().sql(countSql), request, segmentProfileCodes, sourceSystemProfileIds)
                .map((row, metadata) -> row.get(0, Long.class))
                .one();

        return Mono.zip(profilesMono, totalMono)
                .flatMap(t -> buildListItemPage(t.getT1(), t.getT2(), pageable));
    }

    private DatabaseClient.GenericExecuteSpec bindSearchParams(DatabaseClient.GenericExecuteSpec spec,
                                                                ProfileSearchRequest request,
                                                                List<String> segmentProfileCodes,
                                                                List<Long> sourceSystemProfileIds) {
        Short status = request.getStatus() != null ? request.getStatus() : (short) 1;
        boolean keywordActive = StringUtils.hasText(request.getKeyword());
        String keywordPattern = keywordActive ? "%" + request.getKeyword().toLowerCase() + "%" : "%";
        boolean customerTypeActive = StringUtils.hasText(request.getCustomerType());
        boolean customerGroupActive = StringUtils.hasText(request.getCustomerGroup());
        boolean segmentActive = segmentProfileCodes != null;
        boolean sourceSystemActive = sourceSystemProfileIds != null;

        return spec
                .bind("status", status)
                .bind("keywordActive", keywordActive)
                .bind("keywordPattern", keywordPattern)
                .bind("customerTypeActive", customerTypeActive)
                .bind("customerType", customerTypeActive ? request.getCustomerType() : "")
                .bind("customerGroupActive", customerGroupActive)
                .bind("customerGroup", customerGroupActive ? request.getCustomerGroup() : "")
                .bind("segmentActive", segmentActive)
                .bind("segmentCodes", (segmentActive ? segmentProfileCodes : List.<String>of()).toArray(new String[0]))
                .bind("sourceSystemActive", sourceSystemActive)
                .bind("sourceSystemProfileIds",
                        (sourceSystemActive ? sourceSystemProfileIds : List.<Long>of()).toArray(new Long[0]));
    }

    private String resolveSortColumn(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return "modified";
        }
        Sort.Order order = pageable.getSort().iterator().next();
        String column = SORTABLE_COLUMNS.get(order.getProperty());
        if (column == null) {
            throw new BusinessException("VALIDATION_ERROR", "Invalid sort field: " + order.getProperty());
        }
        return column;
    }

    private String resolveSortDirection(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return "DESC";
        }
        return pageable.getSort().iterator().next().getDirection().name();
    }

    private Mono<Page<ProfileListItemResponse>> buildListItemPage(List<MasterProfile> profiles, Long total,
                                                                    Pageable pageable) {
        if (profiles.isEmpty()) {
            return Mono.just(new PageImpl<>(List.of(), pageable, total));
        }

        List<String> profileCodes = profiles.stream()
                .map(MasterProfile::getProfileCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        List<Long> profileIds = profiles.stream().map(MasterProfile::getId).collect(Collectors.toList());

        Mono<Map<String, UnomiProfileResponse>> unomiIndexMono = unomiClient.searchProfilesByCodes(profileCodes)
                .map(response -> profileListAssembler.buildUnomiIndex(response.getList()));

        // Batch fetch customer_events cho toàn bộ trang trong một query, group theo masterProfileId
        // (tránh N+1). Mỗi list vẫn giữ thứ tự occurredAt DESC do repository đã sort.
        Mono<Map<Long, List<CustomerEvent>>> eventsByProfileIdMono = customerEventRepository
                .findByMasterProfileIdInOrderByOccurredAtDesc(profileIds)
                .collectList()
                .map(events -> events.stream()
                        .filter(e -> e.getMasterProfileId() != null)
                        .collect(Collectors.groupingBy(CustomerEvent::getMasterProfileId)));

        return Mono.zip(unomiIndexMono, eventsByProfileIdMono)
                .flatMap(t -> {
                    Map<String, UnomiProfileResponse> unomiIndex = t.getT1();
                    Map<Long, List<CustomerEvent>> eventsByProfileId = t.getT2();

                    return Flux.fromIterable(profiles)
                            .flatMapSequential(profile -> assembleListItem(profile, unomiIndex, eventsByProfileId))
                            .collectList()
                            .map(content -> new PageImpl<>(content, pageable, total));
                });
    }

    private Mono<ProfileListItemResponse> assembleListItem(MasterProfile profile,
                                                            Map<String, UnomiProfileResponse> unomiIndex,
                                                            Map<Long, List<CustomerEvent>> eventsByProfileId) {
        Long profileId = profile.getId();
        return identityLinkRepository.findByMasterProfileIdAndStatus(profileId, (short) 1)
                .collectList()
                .flatMap(links -> {
                    Mono<List<String>> sourceSystemsMono = resolveSourceSystems(profileId, links);
                    Mono<String[]> warningMono = resolveWarning(profileId);
                    Mono<LocalDateTime> lastActivityMono = resolveLastActivity(profileId, profile);

                    return Mono.zip(sourceSystemsMono, warningMono, optional(lastActivityMono))
                            .map(t -> profileListAssembler.assemble(
                                    profile,
                                    unomiIndex.get(profile.getProfileCode()),
                                    t.getT2(),
                                    t.getT1(),
                                    t.getT3().orElse(null),
                                    eventsByProfileId.getOrDefault(profileId, Collections.emptyList()),
                                    links));
                });
    }

    /**
     * Lấy danh sách profileCode thuộc một segment từ Unomi.
     *
     * @return {@code Optional.empty()} nếu không lọc segment; {@code Optional.of(list)}
     *         (list có thể rỗng) nếu có lọc.
     */
    private Mono<Optional<List<String>>> resolveSegmentProfileCodes(String segment) {
        if (!StringUtils.hasText(segment)) {
            return Mono.just(Optional.empty());
        }

        return unomiClient.getSegmentMembers(segment, SEGMENT_MEMBER_FETCH_LIMIT)
                .map(response -> {
                    List<UnomiProfileResponse> members = (response == null || response.getList() == null)
                            ? Collections.<UnomiProfileResponse>emptyList()
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
                    return Optional.of(codes);
                });
    }

    /**
     * Thay thế subquery JPA "profile.id IN (SELECT masterProfileId FROM ProfileIdentityLink WHERE
     * sourceSystem=X AND status=1)" của {@code buildSpec} gốc — tách thành query riêng rồi ghép điều
     * kiện {@code id IN (...)} ở tầng service, vì R2DBC không hỗ trợ subquery trong Criteria.
     *
     * @return {@code Optional.empty()} nếu không lọc sourceSystem; {@code Optional.of(list)} nếu có lọc.
     */
    private Mono<Optional<List<Long>>> resolveSourceSystemProfileIds(String sourceSystem) {
        if (!StringUtils.hasText(sourceSystem)) {
            return Mono.just(Optional.empty());
        }
        return identityLinkRepository
                .findDistinctMasterProfileIdBySourceSystemAndStatus(sourceSystem, (short) 1)
                .collectList()
                .map(Optional::of);
    }

    private Mono<List<String>> resolveSourceSystems(Long profileId, List<ProfileIdentityLink> links) {
        Mono<List<ProfileIdentityLink>> linksMono = (links != null)
                ? Mono.just(links)
                : identityLinkRepository.findByMasterProfileIdAndStatus(profileId, (short) 1).collectList();
        return linksMono.map(ls -> ls.stream()
                .map(ProfileIdentityLink::getSourceSystem)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList()));
    }

    // =====================================================================
    // HELPER: resolveWarning  returns String[2] {warningStatus, warningText}
    // =====================================================================

    private Mono<String[]> resolveWarning(Long profileId) {
        return conflictRepository.existsByMasterProfileIdAndResolutionStatus(profileId, (short) 0)
                .flatMap(hasConflict -> {
                    if (Boolean.TRUE.equals(hasConflict)) {
                        return Mono.just(new String[]{"CONFLICT", "Có xung đột"});
                    }
                    return matchCandidateRepository.existsPendingCandidateForProfile(profileId, (short) 0)
                            .map(hasPending -> Boolean.TRUE.equals(hasPending)
                                    ? new String[]{"NEED_REVIEW", "Cần rà soát"}
                                    : new String[]{"NORMAL", "Bình thường"});
                });
    }

    // =====================================================================
    // HELPER: resolveLastActivity
    // =====================================================================

    private Mono<LocalDateTime> resolveLastActivity(Long profileId, MasterProfile profile) {
        return attributeValueRepository
                .findTopByMasterProfileIdAndPropertyNameInOrderByReceivedAtDesc(
                        profileId, List.of("lastVisitAt", "lastActivityAt"))
                .flatMap(activityAttr -> Mono.justOrEmpty(activityAttr.getReceivedAt()))
                .switchIfEmpty(Mono.justOrEmpty(profile.getModified()))
                .switchIfEmpty(Mono.justOrEmpty(profile.getCreated()));
    }
}
