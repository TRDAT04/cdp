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
            ProfileListAssembler profileListAssembler) {
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
    }

    // =====================================================================
    // DETAIL
    // =====================================================================

    @Override
    public ProfileDetailResponse getProfileDetail(Long id) {
        MasterProfile profile = masterProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Profile not found: " + id));
        return toDetail(profile);
    }

    private ProfileDetailResponse toDetail(MasterProfile profile) {
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

        return ProfileDetailResponse.builder()
                .id(profile.getId())
                .profileCode(profile.getProfileCode())
                .fullName(profile.getFullName())
                .phone(profile.getPhone())
                .email(profile.getEmail())
                .identityNo(profile.getIdentityNo())
                .gender(profile.getGender())
                .dateOfBirth(profile.getDateOfBirth())
                .customerType(profile.getCustomerType())
                .customerTypeText(mapCustomerTypeText(profile.getCustomerType()))
                .provinceCode(profile.getProvinceCode())
                .provinceName(profile.getProvinceName())
                .unitCode(profile.getUnitCode())
                .unitName(profile.getUnitName())
                .status(profile.getStatus())
                .statusText(mapStatusText(profile.getStatus()))
                .mergedIntoProfileId(profile.getMergedIntoProfileId())
                .lastMergedAt(profile.getLastMergedAt())
                .syncedToUnomiAt(profile.getSyncedToUnomiAt())
                .created(profile.getCreated())
                .modified(profile.getModified())
                .warningStatus(warning[0])
                .warningText(warning[1])
                .sourceSystems(sourceSystems)
                .lastActivityAt(lastActivity)
                .identityLinks(links.stream().map(this::toIdentityLinkResponse).collect(Collectors.toList()))
                .attributeValues(attrs.stream().map(this::toAttributeValueResponse).collect(Collectors.toList()))
                .sourceRecords(records.stream().map(this::toSourceRecordResponse).collect(Collectors.toList()))
                .openConflicts(conflicts.stream().map(this::toConflictResponse).collect(Collectors.toList()))
                .matchCandidates(candidates.stream().map(this::toMatchCandidateSummary).collect(Collectors.toList()))
                .changeLogs(logs.stream().map(this::toChangeLogResponse).collect(Collectors.toList()))
                .latestUnomiSync(latestSync != null ? toUnomiSyncResponse(latestSync) : null)
                .build();
    }

    // =====================================================================
    // LIST / SEARCH
    // =====================================================================

    @Override
    public Page<ProfileListItemResponse> searchProfiles(ProfileSearchRequest request, Pageable pageable) {
        Specification<MasterProfile> spec = buildSpec(request);
        Page<MasterProfile> profilePage = masterProfileRepository.findAll(spec, pageable);

        // Lấy danh sách profileCodes của trang hiện tại
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



    private String mapConflictStatusText(Short status) {
        if (status == null) return null;
        return switch (status) {
            case 0 -> "Chưa giải quyết";
            case 1 -> "Đã giải quyết";
            case 2 -> "Bỏ qua";
            default -> String.valueOf(status);
        };
    }

    private String mapMatchCandidateStatusText(Short status) {
        if (status == null) return null;
        return switch (status) {
            case 0 -> "Chờ xử lý";
            case 1 -> "Đã merge";
            case 2 -> "Đã bỏ qua";
            case 3 -> "Đã từ chối";
            case 4 -> "Hết hạn";
            default -> String.valueOf(status);
        };
    }

    private String mapIdentityLinkStatusText(Short status) {
        if (status == null) return null;
        return switch (status) {
            case 1 -> "Hoạt động";
            case 2 -> "Không hoạt động";
            case 3 -> "Đã merge";
            default -> String.valueOf(status);
        };
    }

    private String mapUnomiSyncStatusText(Short status) {
        if (status == null) return null;
        return switch (status) {
            case 0 -> "Đang chờ";
            case 1 -> "Thành công";
            case 2 -> "Thất bại";
            case 3 -> "Đang thử lại";
            default -> String.valueOf(status);
        };
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

    // =====================================================================
    // ENTITY → DTO MAPPERS
    // =====================================================================

    private ProfileIdentityLinkDetailResponse toIdentityLinkResponse(ProfileIdentityLink e) {
        return ProfileIdentityLinkDetailResponse.builder()
                .id(e.getId())
                .sourceSystem(e.getSourceSystem())
                .sourceCustomerId(e.getSourceCustomerId())
                .identityType(e.getIdentityType())
                .identityValue(e.getIdentityValue())
                .confidenceScore(e.getConfidenceScore())
                .isPrimary(e.getIsPrimary())
                .status(e.getStatus())
                .statusText(mapIdentityLinkStatusText(e.getStatus()))
                .linkedAt(e.getLinkedAt())
                .linkedBy(e.getLinkedBy())
                .build();
    }

    private ProfileAttributeValueDetailResponse toAttributeValueResponse(ProfileAttributeValue e) {
        return ProfileAttributeValueDetailResponse.builder()
                .id(e.getId())
                .sourceSystem(e.getSourceSystem())
                .propertyName(e.getPropertyName())
                .propertyValue(e.getPropertyValue())
                .normalizedValue(e.getNormalizedValue())
                .confidenceScore(e.getConfidenceScore())
                .isSelected(e.getIsSelected())
                .receivedAt(e.getReceivedAt())
                .build();
    }

    private ProfileSourceRecordDetailResponse toSourceRecordResponse(ProfileSourceRecord e) {
        return ProfileSourceRecordDetailResponse.builder()
                .id(e.getId())
                .sourceSystem(e.getSourceSystem())
                .sourceCustomerId(e.getSourceCustomerId())
                .sourceEventId(e.getSourceEventId())
                .mergeStatus(e.getMergeStatus())
                .mergeStatusText(mapMergeStatusText(e.getMergeStatus()))
                .receivedAt(e.getReceivedAt())
                .processedAt(e.getProcessedAt())
                .errorMessage(e.getErrorMessage())
                .rawPayload(e.getRawPayload())
                .normalizedPayload(e.getNormalizedPayload())
                .build();
    }

    private ProfileConflictResponse toConflictResponse(ProfileMergeConflict e) {
        return ProfileConflictResponse.builder()
                .id(e.getId())
                .propertyName(e.getPropertyName())
                .currentValue(e.getCurrentValue())
                .incomingValue(e.getIncomingValue())
                .currentSource(e.getCurrentSource())
                .incomingSource(e.getIncomingSource())
                .conflictReason(e.getConflictReason())
                .resolutionStatus(e.getResolutionStatus())
                .resolutionStatusText(mapConflictStatusText(e.getResolutionStatus()))
                .created(e.getCreated())
                .build();
    }

    private ProfileMatchCandidateSummaryResponse toMatchCandidateSummary(ProfileMatchCandidate e) {
        return ProfileMatchCandidateSummaryResponse.builder()
                .id(e.getId())
                .leftMasterProfileId(e.getLeftMasterProfileId())
                .rightMasterProfileId(e.getRightMasterProfileId())
                .matchScore(e.getMatchScore())
                .matchScorePercent(formatPercent(e.getMatchScore()))
                .matchLevel(e.getMatchLevel())
                .matchLevelText(matchLevelText(e.getMatchLevel()))
                .status(e.getStatus())
                .statusText(mapMatchCandidateStatusText(e.getStatus()))
                .created(e.getCreated())
                .build();
    }

    private ProfileChangeLogResponse toChangeLogResponse(ProfileChangeLog e) {
        return ProfileChangeLogResponse.builder()
                .id(e.getId())
                .eventType(e.getEventType())
                .propertyName(e.getPropertyName())
                .oldValue(e.getOldValue())
                .newValue(e.getNewValue())
                .selectedValue(e.getSelectedValue())
                .oldSource(e.getOldSource())
                .newSource(e.getNewSource())
                .mergeStrategy(e.getMergeStrategy())
                .reason(e.getReason())
                .changedBy(e.getChangedBy())
                .changedAt(e.getChangedAt())
                .build();
    }

    private ProfileUnomiSyncLogDetailResponse toUnomiSyncResponse(ProfileUnomiSyncLog e) {
        return ProfileUnomiSyncLogDetailResponse.builder()
                .id(e.getId())
                .syncType(e.getSyncType())
                .status(e.getStatus())
                .statusText(mapUnomiSyncStatusText(e.getStatus()))
                .errorMessage(e.getErrorMessage())
                .syncedAt(e.getSyncedAt())
                .build();
    }

    // =====================================================================
    // TEXT MAPPERS – dùng cho getProfileDetail
    // =====================================================================

    private String mapStatusText(Short status) {
        if (status == null) return null;
        return switch (status) {
            case 1 -> "Hoạt động";
            case 2 -> "Không hoạt động";
            case 3 -> "Đã merge";
            case 4 -> "Bị chặn";
            case 5 -> "Đã xóa";
            default -> String.valueOf(status);
        };
    }

    private String mapCustomerTypeText(String type) {
        if (type == null) return null;
        return switch (type.toUpperCase()) {
            case "PERSONAL", "CA_NHAN"       -> "Cá nhân";
            case "FREQUENT", "THUONG_XUYEN"  -> "Thường xuyên";
            case "VIP"                       -> "VIP";
            default                          -> type;
        };
    }

    private String mapMergeStatusText(Short status) {
        if (status == null) return null;
        return switch (status) {
            case 0 -> "Chờ xử lý";
            case 1 -> "Đã merge";
            case 2 -> "Xung đột";
            case 3 -> "Bị từ chối";
            case 4 -> "Cần rà soát";
            case 5 -> "Lỗi";
            default -> String.valueOf(status);
        };
    }
}

