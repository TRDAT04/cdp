package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tab "Hoạt động theo mảng dịch vụ chính" (7 mảng: BCCP, TCBC, PPBL, HCC, Logistics, TMĐT, MVNO).
 *
 * <p>Luôn trả về ĐỦ 7 mảng, kể cả mảng "Chưa dùng" ({@code active=false}) — chỉ khác ở chỗ
 * các số liệu bằng 0/null. Dữ liệu suy diễn từ {@code customer_events} (event {@code createOrder})
 * trong cửa sổ {@link #monthsWindow} tháng gần nhất.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileServiceLinesResponse {

    private Long masterProfileId;

    /** Cửa sổ thời gian xét (tháng), vd 12. */
    private int monthsWindow;

    /** Đủ 7 mảng dịch vụ. */
    private List<ServiceLineBlock> serviceLines;

    // ----------------------------------------------------------------

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceLineBlock {

        /** Mã mảng: BCCP, TCBC, PPBL, HCC, LOGISTICS, TMDT, MVNO. */
        private String code;

        /** Nhãn tiếng Việt (TẠM). */
        private String name;

        /** Đang dùng = có >=1 giao dịch trong cửa sổ xét. */
        private boolean active;

        /** "Đang dùng" / "Chưa dùng". */
        private String statusText;

        // --- Metric TÍNH ĐƯỢC từ customer_events (createOrder) ---

        /** Tổng doanh thu (sum amount) trong cửa sổ xét. */
        private BigDecimal totalRevenue;

        /** Tổng số đơn (count createOrder) trong cửa sổ xét. */
        private Long totalOrders;

        /** Trung bình số đơn / tháng = totalOrders / monthsWindow. */
        private BigDecimal avgOrdersPerMonth;

        /** Tổng COD (sum amount với paymentMethod=COD). Chỉ là TỔNG — xem GAP bên dưới. */
        private BigDecimal codTotal;

        /**
         * GAP — các field spec yêu cầu nhưng HIỆN CHƯA CÓ NGUỒN DỮ LIỆU trong
         * {@code customer_events.properties}, nên để {@code null} ở bước demo.
         * Danh sách này liệt kê tên field còn thiếu theo từng mảng để FE/nghiệp vụ nắm.
         * Ví dụ: BCCP → "tỷ lệ phát thành công", "tỷ lệ hoàn", "thời gian giao TB",
         * "COD đã thu/chưa thu/đối soát", "bảng đóng góp theo nguồn (nguồn, vai trò, số đơn, COD)";
         * TCBC → "doanh thu phí dịch vụ", "giá trị TB", "kênh chính", "loại GD nhiều nhất";
         * Logistics → "điểm kho", "sản lượng fulfillment", "SLA giao hàng", "tồn kho SKU",
         * "tỷ lệ giao đúng hẹn".
         * (Field "vai trò" và "Tín hiệu/Rủi ro" cố ý CHƯA làm ở bước này.)
         */
        private List<String> pendingFields;
    }
}
