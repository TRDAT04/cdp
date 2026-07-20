package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Tab "Hồ sơ đa nguồn": bảng so sánh field-by-field giữa các nguồn.
 * <p>Được pivot từ {@code profile_attribute_values}: mỗi {@link Row} là 1 trường
 * (propertyName), mỗi cột là 1 nguồn (sourceSystem), cộng cột "Hồ sơ chuẩn"
 * (giá trị được chọn - isSelected).</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMultiSourceComparisonResponse {

    /** Danh sách nguồn (cột) xuất hiện trong dữ liệu, đã distinct + sort. */
    private List<String> sources;

    private List<Row> rows;

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
         * và cờ báo khác biệt so với masterValue.
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
