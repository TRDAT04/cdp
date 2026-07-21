package vn.vnpost.cdp.profile.assembler;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import vn.vnpost.cdp.profile.dto.query.*;
import vn.vnpost.cdp.profile.dto.query.ProfileSummaryResponse.LastInteraction;
import vn.vnpost.cdp.profile.dto.query.ProfileSummaryResponse.TagItem;
import vn.vnpost.cdp.profile.entity.*;
import vn.vnpost.cdp.unomi.dto.UnomiProfileProperties;
import vn.vnpost.cdp.unomi.dto.UnomiProfileResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
public class ProfileDetailAssembler {

    /** Nhãn tiếng Việt cho các propertyName phổ biến (dùng cho tab Hồ sơ đa nguồn). */
    private static final Map<String, String> PROPERTY_LABELS = Map.ofEntries(
            Map.entry("fullName", "Họ tên"),
            Map.entry("phone", "SĐT"),
            Map.entry("email", "Email"),
            Map.entry("identityNo", "CCCD/MST"),
            Map.entry("gender", "Giới tính"),
            Map.entry("dateOfBirth", "Ngày sinh"),
            Map.entry("customerType", "Loại khách hàng"),
            Map.entry("provinceCode", "Mã tỉnh/TP"),
            Map.entry("provinceName", "Tỉnh/TP"),
            Map.entry("unitCode", "Mã bưu cục"),
            Map.entry("unitName", "Bưu cục")
    );

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

    // =====================================================================
    // TAB 1: TỔNG QUAN
    // =====================================================================
    public ProfileOverviewResponse assembleOverview(
            MasterProfile profile,
            UnomiProfileResponse unomiData,
            List<ProfileIdentityLink> links) {

        List<String> segments = (unomiData != null && !CollectionUtils.isEmpty(unomiData.getSegments()))
                ? unomiData.getSegments()
                : Collections.emptyList();

        return ProfileOverviewResponse.builder()
                .id(profile.getId())
                .profileCode(profile.getProfileCode())
                .fullName(profile.getFullName())
                .gender(profile.getGender())
                .dateOfBirth(profile.getDateOfBirth())
                .phone(profile.getPhone())
                .email(profile.getEmail())
                .identityNoMasked(maskIdentityNo(profile.getIdentityNo()))
                .postId(resolvePostId(links))
                .customerType(profile.getCustomerType())
                .customerTypeText(mapCustomerTypeText(profile.getCustomerType()))
                .provinceCode(profile.getProvinceCode())
                .provinceName(profile.getProvinceName())
                .unitCode(profile.getUnitCode())
                .unitName(profile.getUnitName())
                .segments(segments)
                .build();
    }

    // =====================================================================
    // SUMMARY
    // =====================================================================

    /**
     * Tổng hợp nhanh một profile để hiển thị trên widget/card header.
     *
     * @param profile    bản ghi MasterProfile
     * @param unomiData  dữ liệu Unomi (nullable – graceful degradation)
     * @param links      danh sách identity links (để lấy activeSystems + channel)
     */
    public ProfileSummaryResponse assembleSummary(
            MasterProfile profile,
            UnomiProfileResponse unomiData,
            List<ProfileIdentityLink> links) {

        // --- uid: lấy profileCode, format UID-xxxx nếu thuần số ---
        String uid = formatUid(profile.getProfileCode());

        // --- tags: customerType + status ---
        List<TagItem> tags = buildTags(profile);

        // --- activeSystems: distinct sourceSystem từ active links ---
        List<String> activeSystems = CollectionUtils.isEmpty(links)
                ? Collections.emptyList()
                : links.stream()
                        .map(ProfileIdentityLink::getSourceSystem)
                        .filter(s -> s != null && !s.isBlank())
                        .distinct()
                        .collect(Collectors.toList());

        // --- lastInteraction: ưu tiên Unomi lastVisit, fallback modified ---
        LastInteraction lastInteraction = buildLastInteraction(profile, unomiData);

        // --- totalOrders, totalRevenue: từ Unomi props ---
        Long totalOrders = null;
        BigDecimal totalRevenue = null;
        if (unomiData != null && unomiData.getProperties() != null) {
            UnomiProfileProperties props = unomiData.getProperties();
            totalOrders = props.getPurchaseCount() != null
                    ? props.getPurchaseCount().longValue() : null;
            totalRevenue = props.getTotalSpent();
        }

        // --- profileCompleteness: tỉ lệ field không null ---
        double completeness = calcCompleteness(profile);

        return ProfileSummaryResponse.builder()
                .fullName(profile.getFullName())
                .uid(uid)
                .tags(tags)
                .serviceLines(Collections.emptyList())
                .activeSystems(activeSystems)
                .lastInteraction(lastInteraction)
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .loyaltyPoints(null)
                .profileCompleteness(completeness)
                .build();
    }

    // --- helpers cho assembleSummary ---

    private String formatUid(String profileCode) {
        if (profileCode == null) return null;
        // Nếu thuần số và < 10 ký tự thì pad thành UID-0000123456
        if (profileCode.matches("\\d+") && profileCode.length() <= 10) {
            return "UID-" + String.format("%010d", Long.parseLong(profileCode));
        }
        return profileCode;
    }

    private List<TagItem> buildTags(MasterProfile profile) {
        List<TagItem> tags = new ArrayList<>();
        if (profile.getCustomerType() != null) {
            tags.add(TagItem.builder()
                    .code(profile.getCustomerType().toUpperCase())
                    .text(mapCustomerTypeText(profile.getCustomerType()))
                    .build());
        }
        if (profile.getCustomerGroup() != null) {
            tags.add(TagItem.builder()
                    .code(profile.getCustomerGroup())
                    .text(mapCustomerGroupText(profile.getCustomerGroup()))
                    .build());
        }
        if (profile.getStatus() != null) {
            String statusCode = switch (profile.getStatus()) {
                case 1 -> "ACTIVE";
                case 2 -> "INACTIVE";
                case 3 -> "MERGED";
                case 4 -> "BLOCKED";
                case 5 -> "DELETED";
                default -> "STATUS_" + profile.getStatus();
            };
            tags.add(TagItem.builder()
                    .code(statusCode)
                    .text(mapStatusText(profile.getStatus()))
                    .build());
        }
        return tags;
    }

    private LastInteraction buildLastInteraction(
            MasterProfile profile,
            UnomiProfileResponse unomiData) {

        if (unomiData != null && unomiData.getProperties() != null
                && unomiData.getProperties().getLastVisit() != null) {
            return LastInteraction.builder()
                    .time(unomiData.getProperties().getLastVisit())
                    .channel(null) // chưa có nguồn dữ liệu channel thật
                    .build();
        }

        if (profile.getModified() != null) {
            return LastInteraction.builder()
                    .time(profile.getModified().toInstant(java.time.ZoneOffset.UTC))
                    .channel(null)
                    .build();
        }
        return null;
    }


    private double calcCompleteness(MasterProfile profile) {
        // 10 trường quan trọng: fullName, phone, email, identityNo, gender,
        // dateOfBirth, customerType, provinceCode, unitCode, profileCode
        int total = 10;
        int filled = 0;
        if (profile.getFullName()     != null && !profile.getFullName().isBlank())     filled++;
        if (profile.getPhone()        != null && !profile.getPhone().isBlank())        filled++;
        if (profile.getEmail()        != null && !profile.getEmail().isBlank())        filled++;
        if (profile.getIdentityNo()   != null && !profile.getIdentityNo().isBlank())   filled++;
        if (profile.getGender()       != null && !profile.getGender().isBlank())       filled++;
        if (profile.getDateOfBirth()  != null)                                         filled++;
        if (profile.getCustomerType() != null && !profile.getCustomerType().isBlank()) filled++;
        if (profile.getProvinceCode() != null && !profile.getProvinceCode().isBlank()) filled++;
        if (profile.getUnitCode()     != null && !profile.getUnitCode().isBlank())     filled++;
        if (profile.getProfileCode()  != null && !profile.getProfileCode().isBlank())  filled++;
        return BigDecimal.valueOf((double) filled / total)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    // =====================================================================
    // TAB 2: HỒ SƠ LIÊN KẾT
    // =====================================================================
    public List<ProfileIdentityLinkDetailResponse> toIdentityLinkResponses(List<ProfileIdentityLink> links) {
        if (CollectionUtils.isEmpty(links)) return Collections.emptyList();
        return links.stream().map(this::toIdentityLinkResponse).collect(Collectors.toList());
    }

    // Thứ tự ưu tiên nguồn khi không có record nào isSelected = true
    private static final List<String> SOURCE_PRIORITY =
            List.of("CAS", "MYVNPOST", "CRM", "POSTID", "PNS");

    // =====================================================================
    // TAB 3: HỒ SƠ ĐA NGUỒN (pivot attribute values)
    // =====================================================================
    public ProfileMultiSourceComparisonResponse assembleMultiSource(
            MasterProfile profile, List<ProfileAttributeValue> attrs) {

        if (CollectionUtils.isEmpty(attrs)) {
            return ProfileMultiSourceComparisonResponse.builder()
                    .sources(Collections.emptyList())
                    .rows(Collections.emptyList())
                    .build();
        }

        // Cột nguồn: distinct sourceSystem, sắp xếp ổn định
        TreeSet<String> sourceSet = attrs.stream()
                .map(ProfileAttributeValue::getSourceSystem)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> sources = new ArrayList<>(sourceSet);

        // Group theo propertyName, giữ thứ tự xuất hiện
        Map<String, List<ProfileAttributeValue>> byProperty = attrs.stream()
                .filter(a -> a.getPropertyName() != null)
                .collect(Collectors.groupingBy(
                        ProfileAttributeValue::getPropertyName,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<ProfileMultiSourceComparisonResponse.Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<ProfileAttributeValue>> entry : byProperty.entrySet()) {
            String propertyName = entry.getKey();
            List<ProfileAttributeValue> values = entry.getValue();

            // --- Bước 1: build raw map nguồn -> display value ---
            Map<String, String> rawBySource = new LinkedHashMap<>();
            String masterValue = null;

            for (ProfileAttributeValue av : values) {
                String display = displayValue(av);
                if (av.getSourceSystem() != null && !av.getSourceSystem().isBlank()) {
                    rawBySource.put(av.getSourceSystem(), display);
                }
                // Ưu tiên record được đánh dấu isSelected = true
                if (Boolean.TRUE.equals(av.getIsSelected()) && masterValue == null) {
                    masterValue = display;
                }
            }

            // --- Bước 2: fallback masterValue theo thứ tự ưu tiên nguồn ---
            if (masterValue == null) {
                masterValue = resolveMasterValue(rawBySource, values);
            }

            // --- Bước 3: build valuesBySource với different per-source ---
            final String finalMaster = masterValue;
            Map<String, ProfileMultiSourceComparisonResponse.SourceValue> valuesBySource =
                    new LinkedHashMap<>();
            // Đảm bảo tất cả nguồn đã biết đều xuất hiện (kể cả nguồn không có field này)
            for (String src : sources) {
                String val = rawBySource.get(src); // null nếu nguồn không có field
                boolean diff = (val != null) && isDifferent(val, finalMaster);
                valuesBySource.put(src, ProfileMultiSourceComparisonResponse.SourceValue.builder()
                        .value(val)
                        .different(diff)
                        .build());
            }

            rows.add(ProfileMultiSourceComparisonResponse.Row.builder()
                    .propertyName(propertyName)
                    .propertyLabel(PROPERTY_LABELS.getOrDefault(propertyName, propertyName))
                    .masterValue(masterValue)
                    .valuesBySource(valuesBySource)
                    .build());
        }

        return ProfileMultiSourceComparisonResponse.builder()
                .sources(sources)
                .rows(rows)
                .build();
    }

    /**
     * Chọn masterValue khi không có record nào isSelected = true.
     * Thứ tự ưu tiên: SOURCE_PRIORITY (CAS > MYVNPOST > CRM > POSTID > PNS),
     * nếu không khớp bất kỳ nguồn nào thì lấy record mới nhất (receivedAt lớn nhất).
     */
    private String resolveMasterValue(Map<String, String> rawBySource,
                                      List<ProfileAttributeValue> values) {
        // 1. Theo thứ tự ưu tiên nguồn
        for (String preferred : SOURCE_PRIORITY) {
            String v = rawBySource.get(preferred);
            if (v != null && !v.isBlank()) return v;
        }
        // 2. Fallback: nguồn có receivedAt gần nhất
        return values.stream()
                .filter(av -> displayValue(av) != null && !displayValue(av).isBlank())
                .max(java.util.Comparator.comparing(
                        av -> av.getReceivedAt() != null ? av.getReceivedAt()
                                : java.time.LocalDateTime.MIN))
                .map(this::displayValue)
                .orElse(null);
    }

    /**
     * So sánh exact match (chỉ trim khoảng trắng đầu/cuối, giữ nguyên dấu và hoa/thường).
     * Trả về true nếu sourceVal khác masterVal sau khi trim.
     */
    private boolean isDifferent(String sourceVal, String masterVal) {
        if (masterVal == null || masterVal.isBlank()) return false;
        if (sourceVal == null) return false;
        return !sourceVal.trim().equals(masterVal.trim());
    }

    // =====================================================================
    // TAB 4: ĐỊA CHỈ
    // =====================================================================
    public ProfileAddressResponse assembleAddress(MasterProfile profile) {
        return ProfileAddressResponse.builder()
                .provinceCode(profile.getProvinceCode())
                .provinceName(profile.getProvinceName())
                .unitCode(profile.getUnitCode())
                .unitName(profile.getUnitName())
                .build();
    }

    // =====================================================================
    // TAB 6: HÀNH VI SỐ (Unomi)
    // =====================================================================
    public ProfileDigitalBehaviorResponse assembleBehavior(UnomiProfileResponse unomiData) {
        ProfileDigitalBehaviorResponse.ProfileDigitalBehaviorResponseBuilder builder =
                ProfileDigitalBehaviorResponse.builder()
                        .segments(Collections.emptyList());

        if (unomiData != null) {
            builder.segments(CollectionUtils.isEmpty(unomiData.getSegments())
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

    // =====================================================================
    // TAB 10: NHẬT KÝ
    // =====================================================================
    public ProfileChangeLogsResponse assembleChangeLogs(
            List<ProfileChangeLog> logs, ProfileUnomiSyncLog latestSync) {
        return ProfileChangeLogsResponse.builder()
                .changeLogs(CollectionUtils.isEmpty(logs)
                        ? Collections.emptyList()
                        : logs.stream().map(this::toChangeLogResponse).collect(Collectors.toList()))
                .latestUnomiSync(latestSync != null ? toUnomiSyncResponse(latestSync) : null)
                .build();
    }

    // =====================================================================
    // Helpers riêng cho các tab tách
    // =====================================================================
    private String displayValue(ProfileAttributeValue av) {
        if (av.getNormalizedValue() != null && !av.getNormalizedValue().isBlank()) {
            return av.getNormalizedValue();
        }
        return av.getPropertyValue();
    }

    private String resolvePostId(List<ProfileIdentityLink> links) {
        if (CollectionUtils.isEmpty(links)) return null;
        return links.stream()
                .filter(l -> "POSTID".equalsIgnoreCase(l.getSourceSystem()))
                .map(l -> l.getIdentityValue() != null ? l.getIdentityValue() : l.getSourceCustomerId())
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** Che phần giữa CCCD, giữ 3 ký tự đầu và 3 ký tự cuối. */
    private String maskIdentityNo(String identityNo) {
        if (identityNo == null) return null;
        String v = identityNo.trim();
        if (v.length() <= 6) return v;
        String head = v.substring(0, 3);
        String tail = v.substring(v.length() - 3);
        return head + "*".repeat(v.length() - 6) + tail;
    }

    private ProfileIdentityLinkDetailResponse toIdentityLinkResponse(ProfileIdentityLink e) {
        return ProfileIdentityLinkDetailResponse.builder()
                .id(e.getId())
                .sourceSystem(e.getSourceSystem())
                .sourceCustomerId(e.getSourceCustomerId())
                .identityType(e.getIdentityType())
                .identityValue(e.getIdentityValue())
                .confidenceScore(e.getConfidenceScore())
                .confidenceLevel(mapConfidenceLevel(e.getConfidenceScore()))
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

    private String mapCustomerGroupText(String customerGroup) {
        if (customerGroup == null) return null;
        return customerGroup; // hiển thị đúng như dữ liệu nguồn, không cần dịch
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

    /**
     * Quy đổi điểm số tin cậy (0–100) sang nhãn hiển thị.
     * <ul>
     *   <li>≥ 90 → "Cao"</li>
     *   <li>70 – 89 → "Trung bình"</li>
     *   <li>&lt; 70 → "Thấp"</li>
     * </ul>
     */
    private String mapConfidenceLevel(BigDecimal score) {
        if (score == null) return null;
        int v = score.intValue();
        if (v >= 90) return "Cao";
        if (v >= 70) return "Trung bình";
        return "Thấp";
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
