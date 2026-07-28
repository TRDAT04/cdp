package vn.vnpost.example.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Tab "Hồ sơ đa nguồn": bảng so sánh field-by-field giữa các nguồn.
 * <p>Được pivot từ {@code profile_attribute_values} + {@code profile_identity_links}:
 * mỗi {@link Row} là 1 trường (propertyName), mỗi cột là 1 nguồn ({@link SourceInfo#code}),
 * cộng cột "Hồ sơ chuẩn" (masterValue). Các row được gom theo {@link Group} để khớp UI.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMultiSourceComparisonResponse {

    /**
     * Danh sách nguồn (cột) mà profile thực sự có dữ liệu, theo thứ tự hiển thị.
     * Nguồn nào profile chưa có link/source record sẽ bị loại khỏi mảng này
     * (không hiển thị cột trống).
     */
    private List<SourceInfo> sources;

    /** Các nhóm field (ĐỊNH DANH, TÀI CHÍNH/HỢP ĐỒNG...) — thay cho danh sách rows phẳng cũ. */
    private List<Group> groups;

    /**
     * Một nguồn dữ liệu (cột) trong bảng so sánh.
     * <ul>
     *   <li>{@code code} – mã nguồn (CAS, CRM, MYVNPOST, POSTID, PNS_DINGDONG, PAYPOST...)</li>
     *   <li>{@code sourceCustomerId} – mã khách hàng của profile tại nguồn đó
     *       (từ {@code profile_source_records}/{@code profile_identity_links}); null nếu chưa có</li>
     * </ul>
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceInfo {
        private String code;
        private String sourceCustomerId;
    }

    /** Một nhóm field hiển thị trên UI (VD: "ĐỊNH DANH", "TÀI CHÍNH/HỢP ĐỒNG"). */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Group {
        private String groupName;
        private List<Row> rows;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private String propertyName;
        private String propertyLabel;

        /** Giá trị "Hồ sơ chuẩn" (từ attribute có isSelected = true, hoặc nguồn ưu tiên). */
        private String masterValue;

        /**
         * Map nguồn → {@link SourceValue}: giá trị của trường này tại nguồn đó
         * và cờ báo khác biệt so với masterValue. Key trùng {@link SourceInfo#code}.
         */
        private Map<String, SourceValue> valuesBySource;
    }

    /**
     * Giá trị của 1 field tại 1 nguồn cụ thể.
     * <ul>
     *   <li>{@code value} – giá trị thô (null nếu nguồn không cung cấp field này)</li>
     *   <li>{@code different} – true nếu {@code value} khác {@code masterValue}
     *       (sau chuẩn hóa); false nếu giống hoặc value null</li>
     * </ul>
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceValue {
        private String value;
        private boolean different;
    }
}
