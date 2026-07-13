package vn.vnpost.cdp.profile.assembler;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import vn.vnpost.cdp.profile.dto.query.*;
import vn.vnpost.cdp.profile.entity.*;
import vn.vnpost.cdp.unomi.dto.UnomiProfileProperties;
import vn.vnpost.cdp.unomi.dto.UnomiProfileResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProfileDetailAssembler {

    public ProfileDetailResponse assemble(
            MasterProfile profile,
            UnomiProfileResponse unomiData,
            String[] warning,
            List<String> sourceSystems,
            LocalDateTime lastActivity,
            List<ProfileIdentityLink> links,
            List<ProfileAttributeValue> attrs,
            List<ProfileSourceRecord> records,
            List<ProfileMergeConflict> conflicts,
            List<ProfileMatchCandidate> candidates,
            List<ProfileChangeLog> logs,
            ProfileUnomiSyncLog latestSync) {

        ProfileDetailResponse.ProfileDetailResponseBuilder builder = ProfileDetailResponse.builder()
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
                .latestUnomiSync(latestSync != null ? toUnomiSyncResponse(latestSync) : null);

        if (unomiData != null) {
            builder.segments(
                    CollectionUtils.isEmpty(unomiData.getSegments())
                            ? Collections.emptyList()
                            : unomiData.getSegments());

            UnomiProfileProperties props = unomiData.getProperties();
            if (props != null) {
                builder.firstVisit(props.getFirstVisit())
                        .previousVisit(props.getPreviousVisit())
                        .lastVisit(props.getLastVisit())
                        .nbOfVisits(props.getNbOfVisits())
                        .purchaseCount(props.getPurchaseCount())
                        .totalSpent(props.getTotalSpent())
                        .lastTransactionDate(props.getLastTransactionDate());
            }
        }

        return builder.build();
    }

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
}
