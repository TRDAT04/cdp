package vn.vnpost.example.profile.assembler;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import vn.vnpost.example.customer_event.entity.CustomerEvent;
import vn.vnpost.example.profile.dto.query.*;
import vn.vnpost.example.profile.enums.CustomerType;
import vn.vnpost.example.profile.enums.IdentityType;
import vn.vnpost.example.profile.enums.ServiceLine;
import vn.vnpost.example.profile.service.serviceline.ServiceCodeMapper;
import vn.vnpost.example.profile.dto.query.ProfileSummaryResponse.LastInteraction;
import vn.vnpost.example.profile.dto.query.ProfileSummaryResponse.TagItem;
import vn.vnpost.example.profile.entity.*;
import vn.vnpost.example.unomi.dto.UnomiProfileProperties;
import vn.vnpost.example.unomi.dto.UnomiProfileResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProfileDetailAssembler {

    /** Số dịch vụ chính tối đa hiển thị trên summary/list. */
    private static final int SERVICE_LINE_LIMIT = 5;

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
                .taxCode(profile.getTaxCode())
                .postId(resolvePostId(links))
                .identities(buildIdentities(links))
                .customerType(profile.getCustomerType())
                .customerTypeText(mapCustomerTypeText(profile.getCustomerType()))
                .customerTier(profile.getCustomerTier())
                .provinceCode(profile.getProvinceCode())
                .provinceName(profile.getProvinceName())
                .unitCode(profile.getUnitCode())
                .unitName(profile.getUnitName())
                .segments(segments)
                // Placeholder: chưa có logic tính vai trò giao dịch (chờ sender/receiver ở event createOrder).
                .transactionRoles(buildTransactionRolesPlaceholder())
                .build();
    }

    /**
     * Placeholder cho "Vai trò giao dịch" — trả về object rỗng để FE ghép giao diện.
     * TODO: thay bằng logic thật sau khi event createOrder có field sender/receiver.
     */
    private TransactionRolesResponse buildTransactionRolesPlaceholder() {
        return TransactionRolesResponse.builder()
                .primaryRole(null)
                .roles(Collections.emptyList())
                .senderCount(null)
                .receiverCount(null)
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
     * @param events     customer_events (nullable) — để suy ra mảng dịch vụ chính
     */
    public ProfileSummaryResponse assembleSummary(
            MasterProfile profile,
            UnomiProfileResponse unomiData,
            List<ProfileIdentityLink> links,
            List<CustomerEvent> events) {

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
                .serviceLines(CustomerEventDerivations.resolveTopServiceLines(events, SERVICE_LINE_LIMIT))
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
    // TAB 3: HỒ SƠ ĐA NGUỒN (pivot attribute values + identity links)
    // =====================================================================

    /** Thứ tự hiển thị nguồn trên tab Hồ sơ đa nguồn (theo mockup FE). */
    private static final List<String> MULTI_SOURCE_ORDER =
            List.of("CAS", "CRM", "MYVNPOST", "POSTID", "PNS_DINGDONG", "PAYPOST");

    public ProfileMultiSourceComparisonResponse assembleMultiSource(
            MasterProfile profile,
            List<ProfileAttributeValue> attrs,
            List<ProfileIdentityLink> links,
            List<ProfileSourceRecord> records) {

        // --- Cột nguồn: chỉ những nguồn profile thực sự có dữ liệu (loại nguồn trống) ---
        List<ProfileMultiSourceComparisonResponse.SourceInfo> sources =
                buildSourceInfos(attrs, links, records);
        List<String> sourceCodes = sources.stream()
                .map(ProfileMultiSourceComparisonResponse.SourceInfo::getCode)
                .collect(Collectors.toList());

        // --- Pivot attribute values -> (rawBySource, masterValue) theo từng propertyName ---
        Map<String, Map<String, String>> rawByProp = new LinkedHashMap<>();
        Map<String, String> masterByProp = new HashMap<>();
        if (!CollectionUtils.isEmpty(attrs)) {
            pivotAttrs(attrs, rawByProp, masterByProp);
        }

        // --- Nhóm ĐỊNH DANH ---
        List<ProfileMultiSourceComparisonResponse.Row> identityRows = new ArrayList<>();
        identityRows.add(buildRow("fullName", "Họ tên", masterByProp.get("fullName"),
                rawByProp.getOrDefault("fullName", Map.of()), sourceCodes));
        identityRows.add(buildRow("phone", "SĐT", masterByProp.get("phone"),
                rawByProp.getOrDefault("phone", Map.of()), sourceCodes));
        identityRows.add(buildRow("email", "Email", masterByProp.get("email"),
                rawByProp.getOrDefault("email", Map.of()), sourceCodes));
        // Row gộp CCCD (cá nhân) / MST (doanh nghiệp) — ưu tiên CCCD (đã che), fallback MST
        identityRows.add(buildIdentityNoOrTaxRow(profile, rawByProp, sourceCodes));
        identityRows.add(buildRow("gender", "Giới tính", masterByProp.get("gender"),
                rawByProp.getOrDefault("gender", Map.of()), sourceCodes));
        identityRows.add(buildRow("dateOfBirth", "Ngày sinh", masterByProp.get("dateOfBirth"),
                rawByProp.getOrDefault("dateOfBirth", Map.of()), sourceCodes));
        // PostID lấy theo từng nguồn từ profile_identity_links (identity_type = POST_ID)
        identityRows.add(buildRow("postId", "PostID", resolvePostId(links),
                buildPostIdBySource(links), sourceCodes));

        // --- Nhóm TÀI CHÍNH/HỢP ĐỒNG ---
        List<ProfileMultiSourceComparisonResponse.Row> financeRows = new ArrayList<>();
        // Mã số thuế: lấy giá trị thật từ master_profiles.tax_code (null nếu chưa có)
        financeRows.add(buildRow("taxCode", "Mã số thuế", profile.getTaxCode(),
                rawByProp.getOrDefault("taxCode", Map.of()), sourceCodes));
        // TODO: contract (Hợp đồng) chưa xác định nguồn dữ liệu — để null cho mọi nguồn, chờ tích hợp sau.
        financeRows.add(buildRow("contract", "Hợp đồng", null, Map.of(), sourceCodes));
        // TODO: debt (Công nợ) chưa xác định nguồn dữ liệu — để null cho mọi nguồn, chờ tích hợp sau.
        financeRows.add(buildRow("debt", "Công nợ", null, Map.of(), sourceCodes));

        List<ProfileMultiSourceComparisonResponse.Group> groups = List.of(
                ProfileMultiSourceComparisonResponse.Group.builder()
                        .groupName("ĐỊNH DANH").rows(identityRows).build(),
                ProfileMultiSourceComparisonResponse.Group.builder()
                        .groupName("TÀI CHÍNH/HỢP ĐỒNG").rows(financeRows).build());

        return ProfileMultiSourceComparisonResponse.builder()
                .sources(sources)
                .groups(groups)
                .build();
    }

    /**
     * Danh sách nguồn (cột) mà profile thực sự có dữ liệu = attrs ∪ links ∪ records,
     * sắp theo {@link #MULTI_SOURCE_ORDER} (nguồn ngoài danh mục xếp cuối, sort A-Z).
     * Nguồn không có dữ liệu bị loại (không hiển thị cột trống).
     * {@code sourceCustomerId} ưu tiên lấy từ source records, fallback identity links.
     */
    private List<ProfileMultiSourceComparisonResponse.SourceInfo> buildSourceInfos(
            List<ProfileAttributeValue> attrs,
            List<ProfileIdentityLink> links,
            List<ProfileSourceRecord> records) {

        Map<String, String> customerIdBySource = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(records)) {
            for (ProfileSourceRecord r : records) {
                if (StringUtils.hasText(r.getSourceSystem()) && StringUtils.hasText(r.getSourceCustomerId())) {
                    customerIdBySource.putIfAbsent(r.getSourceSystem(), r.getSourceCustomerId());
                }
            }
        }
        if (!CollectionUtils.isEmpty(links)) {
            for (ProfileIdentityLink l : links) {
                if (StringUtils.hasText(l.getSourceSystem()) && StringUtils.hasText(l.getSourceCustomerId())) {
                    customerIdBySource.putIfAbsent(l.getSourceSystem(), l.getSourceCustomerId());
                }
            }
        }

        Set<String> present = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(attrs)) {
            for (ProfileAttributeValue a : attrs) {
                if (StringUtils.hasText(a.getSourceSystem())) present.add(a.getSourceSystem());
            }
        }
        if (!CollectionUtils.isEmpty(links)) {
            for (ProfileIdentityLink l : links) {
                if (StringUtils.hasText(l.getSourceSystem())) present.add(l.getSourceSystem());
            }
        }
        if (!CollectionUtils.isEmpty(records)) {
            for (ProfileSourceRecord r : records) {
                if (StringUtils.hasText(r.getSourceSystem())) present.add(r.getSourceSystem());
            }
        }

        List<String> ordered = new ArrayList<>();
        for (String code : MULTI_SOURCE_ORDER) {
            if (present.remove(code)) ordered.add(code);
        }
        // Nguồn ngoài danh mục mockup nhưng có data thật → vẫn giữ (không giấu data)
        List<String> extras = new ArrayList<>(present);
        Collections.sort(extras);
        ordered.addAll(extras);

        List<ProfileMultiSourceComparisonResponse.SourceInfo> result = new ArrayList<>();
        for (String code : ordered) {
            result.add(ProfileMultiSourceComparisonResponse.SourceInfo.builder()
                    .code(code)
                    .sourceCustomerId(customerIdBySource.get(code))
                    .build());
        }
        return result;
    }

    /** Pivot attribute values thành rawBySource + masterValue theo từng propertyName. */
    private void pivotAttrs(List<ProfileAttributeValue> attrs,
                            Map<String, Map<String, String>> rawByPropOut,
                            Map<String, String> masterByPropOut) {
        Map<String, List<ProfileAttributeValue>> byProperty = attrs.stream()
                .filter(a -> a.getPropertyName() != null)
                .collect(Collectors.groupingBy(
                        ProfileAttributeValue::getPropertyName,
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<String, List<ProfileAttributeValue>> entry : byProperty.entrySet()) {
            List<ProfileAttributeValue> values = entry.getValue();
            Map<String, String> rawBySource = new LinkedHashMap<>();
            String masterValue = null;
            for (ProfileAttributeValue av : values) {
                String display = displayValue(av);
                if (StringUtils.hasText(av.getSourceSystem())) {
                    rawBySource.put(av.getSourceSystem(), display);
                }
                // Ưu tiên record được đánh dấu isSelected = true
                if (Boolean.TRUE.equals(av.getIsSelected()) && masterValue == null) {
                    masterValue = display;
                }
            }
            if (masterValue == null) {
                masterValue = resolveMasterValue(rawBySource, values);
            }
            rawByPropOut.put(entry.getKey(), rawBySource);
            masterByPropOut.put(entry.getKey(), masterValue);
        }
    }

    /**
     * Row gộp "CCCD/MST": nếu có CCCD (identityNo) → hiển thị CCCD dạng che, nếu không có
     * CCCD nhưng có MST (taxCode) → hiển thị MST. Áp dụng cho cả masterValue và từng nguồn.
     */
    private ProfileMultiSourceComparisonResponse.Row buildIdentityNoOrTaxRow(
            MasterProfile profile,
            Map<String, Map<String, String>> rawByProp,
            List<String> sourceCodes) {

        Map<String, String> idNoRaw = rawByProp.getOrDefault("identityNo", Map.of());
        Map<String, String> taxRaw = rawByProp.getOrDefault("taxCode", Map.of());

        Map<String, String> merged = new LinkedHashMap<>();
        for (String code : sourceCodes) {
            String idNo = idNoRaw.get(code);
            if (StringUtils.hasText(idNo)) {
                merged.put(code, maskIdentityNo(idNo));
            } else if (StringUtils.hasText(taxRaw.get(code))) {
                merged.put(code, taxRaw.get(code));
            }
        }

        String master = StringUtils.hasText(profile.getIdentityNo())
                ? maskIdentityNo(profile.getIdentityNo())
                : profile.getTaxCode();

        return buildRow("identityNoOrTaxCode", "CCCD/MST", master, merged, sourceCodes);
    }

    /**
     * PostID theo từng nguồn từ {@code profile_identity_links}: link identity_type = POST_ID
     * → dùng identityValue; fallback link nguồn POSTID (dữ liệu cũ) → identityValue/sourceCustomerId.
     */
    private Map<String, String> buildPostIdBySource(List<ProfileIdentityLink> links) {
        Map<String, String> map = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(links)) return map;
        for (ProfileIdentityLink l : links) {
            if (!StringUtils.hasText(l.getSourceSystem())) continue;
            if (l.getStatus() != null && l.getStatus() != 1) continue; // chỉ link ACTIVE
            String value = null;
            if (IdentityType.POST_ID.name().equalsIgnoreCase(l.getIdentityType())) {
                value = l.getIdentityValue();
            } else if ("POSTID".equalsIgnoreCase(l.getSourceSystem())) {
                value = StringUtils.hasText(l.getIdentityValue())
                        ? l.getIdentityValue() : l.getSourceCustomerId();
            }
            if (StringUtils.hasText(value)) {
                map.putIfAbsent(l.getSourceSystem(), value);
            }
        }
        return map;
    }

    /** Build 1 Row: điền valuesBySource cho mọi nguồn trong sourceCodes (null nếu nguồn thiếu field). */
    private ProfileMultiSourceComparisonResponse.Row buildRow(
            String propertyName, String propertyLabel, String masterValue,
            Map<String, String> rawBySource, List<String> sourceCodes) {

        Map<String, ProfileMultiSourceComparisonResponse.SourceValue> valuesBySource =
                new LinkedHashMap<>();
        for (String code : sourceCodes) {
            String val = rawBySource.get(code); // null nếu nguồn không có field này
            boolean diff = (val != null) && isDifferent(val, masterValue);
            valuesBySource.put(code, ProfileMultiSourceComparisonResponse.SourceValue.builder()
                    .value(val)
                    .different(diff)
                    .build());
        }
        return ProfileMultiSourceComparisonResponse.Row.builder()
                .propertyName(propertyName)
                .propertyLabel(propertyLabel)
                .masterValue(masterValue)
                .valuesBySource(valuesBySource)
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
    // TAB 6: HÀNH VI SỐ (Unomi + customer_events)
    // =====================================================================

    /**
     * @param events customer_events của profile, đã sort mới nhất trước (nullable)
     */
    public ProfileDigitalBehaviorResponse assembleBehavior(List<CustomerEvent> events) {

        ProfileDigitalBehaviorResponse.ProfileDigitalBehaviorResponseBuilder builder =
                ProfileDigitalBehaviorResponse.builder()
                        .channelsInteracted(Collections.emptyList())
                        .timeline(Collections.emptyList());

        if (!CollectionUtils.isEmpty(events)) {
            // events đã sort occurredAt DESC -> phần tử đầu tiên khớp là mới nhất
            CustomerEvent lastLogin = latestEvent(events, CustomerEventDerivations.EVENT_LOGIN);
            if (lastLogin != null) {
                builder.lastLoginAt(lastLogin.getOccurredAt())
                        .device(CustomerEventDerivations.asString(
                                lastLogin.getProperties(), CustomerEventDerivations.PROP_DEVICE));
            }
            builder.channelsInteracted(resolveChannels(events))
                    .sessionsLast30Days(resolveSessionsLast30Days(events))
                    .recentOrder(resolveRecentOrder(events))
                    .lastCampaignResponse(resolveLastCampaignResponse(events))
                    .timeline(buildTimeline(events));
            // engagementScore: để trống (chưa chốt công thức)
        }

        return builder.build();
    }

    /** Event {@code eventType} mới nhất (events đã sort occurredAt DESC). */
    private CustomerEvent latestEvent(List<CustomerEvent> events, String eventType) {
        return events.stream()
                .filter(e -> eventType.equals(e.getEventType()) && e.getOccurredAt() != null)
                .findFirst()
                .orElse(null);
    }

    /** Số phiên (sessionId distinct) trong 30 ngày gần nhất tính từ thời điểm hiện tại. */
    private Integer resolveSessionsLast30Days(List<CustomerEvent> events) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        long count = events.stream()
                .filter(e -> e.getOccurredAt() != null && !e.getOccurredAt().isBefore(threshold))
                .map(CustomerEvent::getSessionId)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .count();
        return (int) count;
    }

    /** distinct sourceSystem, giữ thứ tự xuất hiện (mới nhất trước). */
    private List<String> resolveChannels(List<CustomerEvent> events) {
        return events.stream()
                .map(CustomerEvent::getSourceSystem)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private ProfileDigitalBehaviorResponse.RecentOrder resolveRecentOrder(List<CustomerEvent> events) {
        return events.stream()
                .filter(e -> CustomerEventDerivations.EVENT_CREATE_ORDER.equals(e.getEventType()))
                .findFirst()
                .map(e -> {
                    Map<String, Object> p = e.getProperties();
                    return ProfileDigitalBehaviorResponse.RecentOrder.builder()
                            .orderId(CustomerEventDerivations.asString(p, CustomerEventDerivations.PROP_ORDER_ID))
                            .amount(CustomerEventDerivations.asBigDecimal(p, CustomerEventDerivations.PROP_AMOUNT))
                            .serviceCode(CustomerEventDerivations.asString(p, CustomerEventDerivations.PROP_SERVICE_CODE))
                            .orderStatus(CustomerEventDerivations.asString(p, CustomerEventDerivations.PROP_ORDER_STATUS))
                            .occurredAt(e.getOccurredAt())
                            .build();
                })
                .orElse(null);
    }

    /**
     * Phản hồi campaign gần nhất (event {@code campaignResponse}, events đã sort DESC).
     * Null nếu không có event nào — chưa có nguồn/data thì FE hiển thị trống.
     */
    private ProfileDigitalBehaviorResponse.LastCampaignResponse resolveLastCampaignResponse(
            List<CustomerEvent> events) {
        CustomerEvent e = latestEvent(events, CustomerEventDerivations.EVENT_CAMPAIGN_RESPONSE);
        if (e == null) return null;
        Map<String, Object> p = e.getProperties();
        String channel = CustomerEventDerivations.asString(p, CustomerEventDerivations.PROP_CHANNEL);
        if (!StringUtils.hasText(channel)) {
            channel = e.getSourceSystem(); // fallback: nguồn phát sinh event
        }
        return ProfileDigitalBehaviorResponse.LastCampaignResponse.builder()
                .campaignCode(CustomerEventDerivations.asString(p, CustomerEventDerivations.PROP_CAMPAIGN_CODE))
                .channel(channel)
                .occurredAt(e.getOccurredAt())
                .build();
    }

    private List<ProfileDigitalBehaviorResponse.TimelineItem> buildTimeline(List<CustomerEvent> events) {
        return events.stream()
                .map(e -> ProfileDigitalBehaviorResponse.TimelineItem.builder()
                        .eventType(e.getEventType())
                        .eventTypeText(mapEventTypeText(e.getEventType()))
                        .sourceSystem(e.getSourceSystem())
                        .occurredAt(e.getOccurredAt())
                        .summary(buildEventSummary(e))
                        .build())
                .collect(Collectors.toList());
    }

    /** Mô tả ngắn cho timeline: đơn hàng hiển thị mã + số tiền, còn lại để null. */
    private String buildEventSummary(CustomerEvent e) {
        if (CustomerEventDerivations.EVENT_CREATE_ORDER.equals(e.getEventType())) {
            String orderId = CustomerEventDerivations.asString(e.getProperties(), CustomerEventDerivations.PROP_ORDER_ID);
            java.math.BigDecimal amount =
                    CustomerEventDerivations.asBigDecimal(e.getProperties(), CustomerEventDerivations.PROP_AMOUNT);
            if (orderId != null && amount != null) {
                return "Đơn " + orderId + " - " + amount.toPlainString();
            }
            if (orderId != null) {
                return "Đơn " + orderId;
            }
        }
        return null;
    }

    private String mapEventTypeText(String eventType) {
        if (eventType == null) return null;
        return switch (eventType) {
            case CustomerEventDerivations.EVENT_LOGIN -> "Đăng nhập";
            case CustomerEventDerivations.EVENT_CREATE_ORDER -> "Tạo đơn hàng";
            case CustomerEventDerivations.EVENT_CAMPAIGN_RESPONSE -> "Phản hồi campaign";
            case "view" -> "Xem trang";
            default -> eventType;
        };
    }

    // =====================================================================
    // TAB: HOẠT ĐỘNG THEO MẢNG DỊCH VỤ (7 mảng)
    // =====================================================================

    /** Cửa sổ thời gian xét cho tab mảng dịch vụ. */
    private static final int SERVICE_LINE_MONTHS_WINDOW = 12;

    /** Các mảng có khái niệm COD (mới build cod object + contributionBySource). Còn lại cod=null, contribution=[]. */
    private static final Set<ServiceLine> COD_SERVICE_LINES =
            java.util.EnumSet.of(ServiceLine.BCCP, ServiceLine.TCBC);

    /** Accumulator nội bộ cho mỗi mảng dịch vụ. */
    private static final class ServiceLineAgg {
        long orders = 0L;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cod = BigDecimal.ZERO;
        /** Đóng góp theo sourceSystem (giữ thứ tự xuất hiện) — cho systemsUsed + contributionBySource. */
        final Map<String, SourceContribution> bySource = new LinkedHashMap<>();
    }

    /** Đóng góp của 1 nguồn cho 1 mảng dịch vụ. */
    private static final class SourceContribution {
        long orders = 0L;
        BigDecimal cod = BigDecimal.ZERO;
    }

    /**
     * Tổng hợp hoạt động theo 7 mảng dịch vụ từ event {@code createOrder} trong
     * {@link #SERVICE_LINE_MONTHS_WINDOW} tháng gần nhất. Luôn trả đủ 7 mảng, dùng chung 1 shape.
     *
     * @param events customer_events của profile (nullable) — không bắt buộc sort trước.
     */
    public ProfileServiceLinesResponse assembleServiceLines(Long masterProfileId, List<CustomerEvent> events) {
        LocalDateTime threshold = LocalDateTime.now().minusMonths(SERVICE_LINE_MONTHS_WINDOW);

        Map<ServiceLine, ServiceLineAgg> byLine = new java.util.EnumMap<>(ServiceLine.class);

        if (!CollectionUtils.isEmpty(events)) {
            for (CustomerEvent e : events) {
                if (!CustomerEventDerivations.EVENT_CREATE_ORDER.equals(e.getEventType())) {
                    continue;
                }
                if (e.getOccurredAt() == null || e.getOccurredAt().isBefore(threshold)) {
                    continue;
                }
                String serviceCode = CustomerEventDerivations.asString(
                        e.getProperties(), CustomerEventDerivations.PROP_SERVICE_CODE);
                ServiceLine line = ServiceCodeMapper.resolve(serviceCode);
                if (line == null) {
                    continue; // serviceCode null / chưa map — ServiceCodeMapper đã log WARN
                }

                ServiceLineAgg agg = byLine.computeIfAbsent(line, k -> new ServiceLineAgg());
                agg.orders++;

                String source = StringUtils.hasText(e.getSourceSystem()) ? e.getSourceSystem() : "UNKNOWN";
                SourceContribution sc = agg.bySource.computeIfAbsent(source, k -> new SourceContribution());
                sc.orders++;

                BigDecimal amount = CustomerEventDerivations.asBigDecimal(
                        e.getProperties(), CustomerEventDerivations.PROP_AMOUNT);
                if (amount != null) {
                    agg.revenue = agg.revenue.add(amount);
                    String paymentMethod = CustomerEventDerivations.asString(
                            e.getProperties(), CustomerEventDerivations.PROP_PAYMENT_METHOD);
                    if ("COD".equalsIgnoreCase(paymentMethod)) {
                        agg.cod = agg.cod.add(amount);
                        sc.cod = sc.cod.add(amount);
                    }
                }
            }
        }

        List<ProfileServiceLinesResponse.ServiceLineBlock> blocks = new ArrayList<>();
        for (ServiceLine line : ServiceLine.values()) {
            ServiceLineAgg agg = byLine.get(line);
            boolean active = agg != null && agg.orders > 0;
            boolean hasCod = COD_SERVICE_LINES.contains(line);

            ProfileServiceLinesResponse.ServiceLineBlock.ServiceLineBlockBuilder b =
                    ProfileServiceLinesResponse.ServiceLineBlock.builder()
                            .code(line.name())
                            .name(line.getLabel())
                            .active(active)
                            .statusText(active ? "Đang dùng" : "Chưa dùng")
                            .systemsUsed(active
                                    ? new ArrayList<>(agg.bySource.keySet())
                                    : Collections.emptyList())
                            .extra(buildServiceLineExtra(line));
            // successDeliveryRate / returnRate / avgDeliveryDays / signal: chưa có nguồn → giữ null (builder default).

            if (active) {
                BigDecimal avgPerMonth = BigDecimal.valueOf(agg.orders)
                        .divide(BigDecimal.valueOf(SERVICE_LINE_MONTHS_WINDOW), 2, RoundingMode.HALF_UP);
                b.totalRevenue(agg.revenue)
                        .totalOrders(agg.orders)
                        .avgOrdersPerMonth(avgPerMonth);
            }
            // Mảng "Chưa dùng": totalRevenue/totalOrders/avgOrdersPerMonth để null.

            // COD object: chỉ mảng có khái niệm COD; total tính được, các field còn lại null.
            if (hasCod) {
                b.cod(ProfileServiceLinesResponse.Cod.builder()
                        .total(active ? agg.cod : null)
                        .collected(null)
                        .outstanding(null)
                        .reconciliationStatus(null)
                        .build());
            }
            // Mảng không có COD (Logistics, TMĐT, PPBL, HCC, MVNO): cod = null (builder default).

            // contributionBySource: chỉ mảng có COD & đang hoạt động; role để null. Còn lại rỗng [].
            if (hasCod && active) {
                List<ProfileServiceLinesResponse.ContributionBySource> contribs = new ArrayList<>();
                for (Map.Entry<String, SourceContribution> en : agg.bySource.entrySet()) {
                    contribs.add(ProfileServiceLinesResponse.ContributionBySource.builder()
                            .source(en.getKey())
                            .role(null)
                            .orderCount(en.getValue().orders)
                            .codContribution(en.getValue().cod)
                            .build());
                }
                b.contributionBySource(contribs);
            } else {
                b.contributionBySource(Collections.emptyList());
            }

            blocks.add(b.build());
        }

        return ProfileServiceLinesResponse.builder()
                .masterProfileId(masterProfileId)
                .monthsWindow(SERVICE_LINE_MONTHS_WINDOW)
                .serviceLines(blocks)
                .build();
    }

    /**
     * Field đặc thù từng mảng (đặt trong {@code extra}). Dùng LinkedHashMap để giữ thứ tự
     * và cho phép value null (Map.of không cho null). BCCP/PPBL/HCC/MVNO → rỗng {@code {}}.
     */
    private Map<String, Object> buildServiceLineExtra(ServiceLine line) {
        Map<String, Object> extra = new LinkedHashMap<>();
        switch (line) {
            case TCBC -> {
                extra.put("mainChannel", null);
                extra.put("topTransactionType", null);
            }
            case LOGISTICS -> {
                extra.put("activeWarehouseCount", null);
                extra.put("fulfillmentVolume", null);
                extra.put("deliverySla", null);
                extra.put("currentStockSku", null);
                extra.put("onTimeDeliveryRate", null);
            }
            case TMDT -> {
                extra.put("gmv", null);
                extra.put("onlineShopCount", null);
                extra.put("mainPlatforms", null);
            }
            default -> {
                // BCCP, PPBL, HCC, MVNO: không có field riêng
            }
        }
        return extra;
    }

    // =====================================================================
    // TAB: CSKH (khiếu nại — join complaintCreated + complaintResolved)
    // =====================================================================

    /** Trạng thái khiếu nại đã tạo, đọc từ event complaintCreated. */
    private static final class ComplaintCreated {
        final String status;
        final LocalDateTime slaDeadline;
        ComplaintCreated(String status, LocalDateTime slaDeadline) {
            this.status = status;
            this.slaDeadline = slaDeadline;
        }
    }

    /** Kết quả xử lý khiếu nại, đọc từ event complaintResolved. */
    private static final class ComplaintResolved {
        final LocalDateTime resolvedAt;
        final BigDecimal satisfactionScore;
        ComplaintResolved(LocalDateTime resolvedAt, BigDecimal satisfactionScore) {
            this.resolvedAt = resolvedAt;
            this.satisfactionScore = satisfactionScore;
        }
    }

    /**
     * Tổng hợp CSKH từ event khiếu nại của profile (đã match vào {@code masterProfileId} —
     * event UNMATCHED có masterProfileId null nên không lọt vào đây). Join complaintCreated +
     * complaintResolved theo {@code complaintId}. Chưa có khiếu nại → cả 4 field null.
     *
     * @param events customer_events của profile (nullable) — không bắt buộc sort trước.
     */
    public ProfileCskhResponse assembleCskh(List<CustomerEvent> events) {
        // complaintId -> trạng thái tạo / kết quả xử lý (events sort DESC → putIfAbsent giữ bản mới nhất)
        Map<String, ComplaintCreated> created = new LinkedHashMap<>();
        Map<String, ComplaintResolved> resolved = new LinkedHashMap<>();

        if (!CollectionUtils.isEmpty(events)) {
            for (CustomerEvent e : events) {
                Map<String, Object> p = e.getProperties();
                String complaintId = CustomerEventDerivations.asString(p, CustomerEventDerivations.PROP_COMPLAINT_ID);
                if (!StringUtils.hasText(complaintId)) {
                    continue;
                }
                if (CustomerEventDerivations.EVENT_COMPLAINT_CREATED.equals(e.getEventType())) {
                    created.putIfAbsent(complaintId, new ComplaintCreated(
                            CustomerEventDerivations.asString(p, CustomerEventDerivations.PROP_STATUS),
                            CustomerEventDerivations.asLocalDateTime(p, CustomerEventDerivations.PROP_SLA_DEADLINE)));
                } else if (CustomerEventDerivations.EVENT_COMPLAINT_RESOLVED.equals(e.getEventType())) {
                    resolved.putIfAbsent(complaintId, new ComplaintResolved(
                            CustomerEventDerivations.asLocalDateTime(p, CustomerEventDerivations.PROP_RESOLVED_AT),
                            CustomerEventDerivations.asBigDecimal(p, CustomerEventDerivations.PROP_SATISFACTION_SCORE)));
                }
            }
        }

        // Chưa từng có khiếu nại → toàn bộ null (không trả 0)
        if (created.isEmpty()) {
            return ProfileCskhResponse.builder().build();
        }

        long totalComplaints = created.size();

        // OPEN và chưa có bản resolved tương ứng
        long openComplaints = created.entrySet().stream()
                .filter(en -> "OPEN".equalsIgnoreCase(en.getValue().status))
                .filter(en -> !resolved.containsKey(en.getKey()))
                .count();

        // AVG satisfactionScore (bỏ qua null); không có điểm nào → null
        List<BigDecimal> scores = resolved.values().stream()
                .map(r -> r.satisfactionScore)
                .filter(s -> s != null)
                .collect(Collectors.toList());
        BigDecimal satisfactionScore = scores.isEmpty()
                ? null
                : scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);

        // slaOnTimeRate (0-1): resolvedAt <= slaDeadline / tổng đã resolved; không có resolved → null
        BigDecimal slaOnTimeRate = null;
        int totalResolved = resolved.size();
        if (totalResolved > 0) {
            long onTime = resolved.entrySet().stream()
                    .filter(en -> {
                        ComplaintResolved r = en.getValue();
                        ComplaintCreated c = created.get(en.getKey());
                        return r.resolvedAt != null && c != null && c.slaDeadline != null
                                && !r.resolvedAt.isAfter(c.slaDeadline);
                    })
                    .count();
            slaOnTimeRate = BigDecimal.valueOf(onTime)
                    .divide(BigDecimal.valueOf(totalResolved), 2, RoundingMode.HALF_UP);
        }

        return ProfileCskhResponse.builder()
                .totalComplaints(totalComplaints)
                .openComplaints(openComplaints)
                .satisfactionScore(satisfactionScore)
                .slaOnTimeRate(slaOnTimeRate)
                .build();
    }

    // =====================================================================
    // TAB: ĐỒNG Ý DỮ LIỆU (Consent Management)
    // =====================================================================

    /** Mục đích cố định (thứ tự hiển thị) → nhãn tiếng Việt. */
    private static final Map<String, String> CONSENT_PURPOSE_LABELS = buildConsentPurposeLabels();
    /** Kênh cố định (thứ tự cột). */
    private static final List<String> CONSENT_CHANNELS = List.of("SMS", "EMAIL", "ZALO_OA", "PUSH");
    /** Mục đích nghĩa vụ dịch vụ — luôn GRANTED, không đọc từ event. */
    private static final String CONSENT_PURPOSE_OPERATIONAL = "OPERATIONAL";
    private static final String CONSENT_STATUS_GRANTED = "GRANTED";
    private static final String CONSENT_STATUS_UNKNOWN = "UNKNOWN";

    private static Map<String, String> buildConsentPurposeLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("OPERATIONAL", "Thông báo vận hành");
        m.put("CUSTOMER_CARE", "Chăm sóc khách hàng");
        m.put("MARKETING", "Tiếp thị & ưu đãi");
        return m;
    }

    /**
     * Ma trận đồng ý từ event {@code consentUpdated} của profile (đã match vào {@code masterProfileId}).
     * Ma trận CỐ ĐỊNH 3 mục đích × 4 kênh; với mỗi cặp (purpose, channel) chỉ lấy event MỚI NHẤT
     * (events sort occurredAt DESC → {@code putIfAbsent} giữ bản mới nhất). OPERATIONAL hard-code GRANTED;
     * 2 mục đích còn lại thiếu event → UNKNOWN. 3 field cấp ngoài lấy từ event consentUpdated mới nhất
     * toàn cục; chưa có event nào → null.
     *
     * @param events customer_events của profile (nullable) — kỳ vọng đã sort occurredAt DESC.
     */
    public ProfileConsentResponse assembleConsent(List<CustomerEvent> events) {
        // "purpose channel" -> status mới nhất (putIfAbsent giữ bản đầu tiên = mới nhất khi sort DESC)
        Map<String, String> latestStatus = new HashMap<>();
        CustomerEvent latestEvent = null;

        if (!CollectionUtils.isEmpty(events)) {
            for (CustomerEvent e : events) {
                if (!CustomerEventDerivations.EVENT_CONSENT_UPDATED.equals(e.getEventType())) {
                    continue;
                }
                // Event mới nhất toàn cục (bất kể purpose/channel) cho 3 field cấp ngoài
                if (latestEvent == null || isNewer(e, latestEvent)) {
                    latestEvent = e;
                }
                Map<String, Object> p = e.getProperties();
                String purpose = CustomerEventDerivations.asString(p, CustomerEventDerivations.PROP_PURPOSE);
                String channel = CustomerEventDerivations.asString(p, CustomerEventDerivations.PROP_CHANNEL);
                String status = CustomerEventDerivations.asString(p, CustomerEventDerivations.PROP_STATUS);
                if (!StringUtils.hasText(purpose) || !StringUtils.hasText(channel) || !StringUtils.hasText(status)) {
                    continue;
                }
                latestStatus.putIfAbsent(consentKey(purpose, channel), status);
            }
        }

        List<ProfileConsentResponse.ConsentRow> matrix = new ArrayList<>();
        for (Map.Entry<String, String> purpose : CONSENT_PURPOSE_LABELS.entrySet()) {
            boolean operational = CONSENT_PURPOSE_OPERATIONAL.equals(purpose.getKey());
            Map<String, String> channels = new LinkedHashMap<>();
            for (String channel : CONSENT_CHANNELS) {
                if (operational) {
                    channels.put(channel, CONSENT_STATUS_GRANTED); // nghĩa vụ dịch vụ, không xin đồng ý
                } else {
                    channels.put(channel,
                            latestStatus.getOrDefault(consentKey(purpose.getKey(), channel), CONSENT_STATUS_UNKNOWN));
                }
            }
            matrix.add(ProfileConsentResponse.ConsentRow.builder()
                    .purpose(purpose.getKey())
                    .purposeLabel(purpose.getValue())
                    .channels(channels)
                    .build());
        }

        return ProfileConsentResponse.builder()
                .consentMatrix(matrix)
                .collectionSource(latestEvent != null ? latestEvent.getSourceSystem() : null)
                .lastUpdated(latestEvent != null ? latestEvent.getOccurredAt() : null)
                .termsVersion(latestEvent != null
                        ? CustomerEventDerivations.asString(latestEvent.getProperties(),
                                CustomerEventDerivations.PROP_TERMS_VERSION)
                        : null)
                .build();
    }

    private static String consentKey(String purpose, String channel) {
        return purpose + ' ' + channel;
    }

    /** So sánh occurredAt (null coi là cũ nhất) — không phụ thuộc hoàn toàn vào thứ tự đầu vào. */
    private static boolean isNewer(CustomerEvent candidate, CustomerEvent current) {
        LocalDateTime a = candidate.getOccurredAt();
        LocalDateTime b = current.getOccurredAt();
        if (a == null) {
            return false;
        }
        if (b == null) {
            return true;
        }
        return a.isAfter(b);
    }

    // =====================================================================
    // TAB 10: NHẬT KÝ
    // =====================================================================

    /** mergeStrategy đánh dấu dòng tạo hồ sơ mới (lưu dạng String trong profile_change_logs). */
    private static final String MERGE_STRATEGY_CREATE_NEW_PROFILE = "CREATE_NEW_PROFILE";

    /**
     * @param logs          change logs HIỂN THỊ (đang là top-20 DESC) — giữ nguyên như cũ.
     * @param allLogs       TOÀN BỘ change logs của hồ sơ (DESC) — chỉ dùng để tính {@code profileSummary}
     *                      (createdAt/createdBySystem cần dòng cũ nhất, có thể nằm ngoài top-20).
     * @param latestSync    lần sync Unomi gần nhất (nullable).
     * @param sourceSystems DISTINCT sourceSystem của hồ sơ — tái sử dụng logic Overview/Detail
     *                      ({@code resolveSourceSystems}), KHÔNG suy từ changeLogs (phạm vi hẹp hơn).
     */
    public ProfileChangeLogsResponse assembleChangeLogs(
            List<ProfileChangeLog> logs, List<ProfileChangeLog> allLogs,
            ProfileUnomiSyncLog latestSync, List<String> sourceSystems) {
        return ProfileChangeLogsResponse.builder()
//                .changeLogs(CollectionUtils.isEmpty(logs)
//                        ? Collections.emptyList()
//                        : logs.stream().map(this::toChangeLogResponse).collect(Collectors.toList()))
//                .latestUnomiSync(latestSync != null ? toUnomiSyncResponse(latestSync) : null)
                .profileSummary(buildProfileSummary(allLogs, latestSync, sourceSystems))
                .build();
    }

    /**
     * Tóm tắt vòng đời hồ sơ từ toàn bộ change logs + lần sync gần nhất. {@code sourceSystems} được
     * truyền vào (tái sử dụng logic Overview/Detail). Hồ sơ chưa có change log nào → createdAt/
     * createdBySystem null, lastUpdatedAt = syncedAt (nếu có).
     */
    private ProfileChangeLogsResponse.ProfileSummary buildProfileSummary(
            List<ProfileChangeLog> allLogs, ProfileUnomiSyncLog latestSync, List<String> sourceSystems) {
        List<ProfileChangeLog> safeLogs = CollectionUtils.isEmpty(allLogs) ? List.of() : allLogs;

        // createdAt = MIN(changedAt) của dòng CREATE_NEW_PROFILE
        LocalDateTime createdAt = safeLogs.stream()
                .filter(l -> MERGE_STRATEGY_CREATE_NEW_PROFILE.equals(l.getMergeStrategy()))
                .map(ProfileChangeLog::getChangedAt)
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        // createdBySystem = newSource của dòng có changedAt sớm nhất
        String createdBySystem = safeLogs.stream()
                .filter(l -> l.getChangedAt() != null)
                .min(java.util.Comparator.comparing(ProfileChangeLog::getChangedAt))
                .map(ProfileChangeLog::getNewSource)
                .orElse(null);

        // lastUpdatedAt = MAX(MAX(changedAt), syncedAt)
        LocalDateTime maxChangedAt = safeLogs.stream()
                .map(ProfileChangeLog::getChangedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime syncedAt = latestSync != null ? latestSync.getSyncedAt() : null;
        LocalDateTime lastUpdatedAt = maxOf(maxChangedAt, syncedAt);

        return ProfileChangeLogsResponse.ProfileSummary.builder()
                .sourceSystems(sourceSystems)
                .createdAt(createdAt)
                .lastUpdatedAt(lastUpdatedAt)
                .profileVersion(null) // master_profiles chưa có cột version/@Version
                .createdBySystem(createdBySystem)
                .build();
    }

    /** MAX của 2 thời điểm, bỏ qua null; cả hai null → null. */
    private static LocalDateTime maxOf(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
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

    /**
     * Gom toàn bộ định danh động của profile thành object {@link ProfileIdentitiesResponse}.
     * Field nào chưa có link tương ứng → null.
     */
    private ProfileIdentitiesResponse buildIdentities(List<ProfileIdentityLink> links) {
        return ProfileIdentitiesResponse.builder()
                .postId(resolvePostId(links))
                .crmId(resolveIdentity(links, IdentityType.CRM_ID))
                .khlCode(resolveIdentity(links, IdentityType.KHL_CODE))
                .appUserId(resolveIdentity(links, IdentityType.APP_USER_ID))
                .deviceId(resolveIdentity(links, IdentityType.DEVICE_ID))
                .cookieId(resolveIdentity(links, IdentityType.COOKIE_ID))
                .paymentId(resolveIdentity(links, IdentityType.PAYMENT_ID))
                .build();
    }

    /** Giá trị định danh ACTIVE gần nhất theo identity_type; null nếu không có. */
    private String resolveIdentity(List<ProfileIdentityLink> links, IdentityType type) {
        if (CollectionUtils.isEmpty(links)) return null;
        return links.stream()
                .filter(l -> type.name().equalsIgnoreCase(l.getIdentityType()))
                .filter(l -> l.getStatus() == null || l.getStatus() == 1)
                .map(ProfileIdentityLink::getIdentityValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    /**
     * PostID: ưu tiên identity_type = POST_ID (chuẩn hóa mới),
     * fallback link nguồn POSTID (dữ liệu cũ chưa chuẩn hóa).
     */
    private String resolvePostId(List<ProfileIdentityLink> links) {
        if (CollectionUtils.isEmpty(links)) return null;
        String byType = resolveIdentity(links, IdentityType.POST_ID);
        if (StringUtils.hasText(byType)) return byType;
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
        return CustomerType.textOf(type);
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

}
