package vn.vnpost.example.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Tab "Hoạt động theo mảng dịch vụ chính" (7 mảng: BCCP, TCBC, PPBL, HCC, Logistics, TMĐT, MVNO).
 *
 * <p>Luôn trả về ĐỦ 7 mảng, kể cả mảng "Chưa dùng" ({@code active=false}). MỌI mảng dùng CHUNG
 * một shape (base); field đặc thù của từng mảng nằm trong {@link ServiceLineBlock#extra}.
 * Dữ liệu suy diễn từ {@code customer_events} (event {@code createOrder}) trong cửa sổ
 * {@link #monthsWindow} tháng gần nhất; field chưa có nguồn dữ liệu để {@code null}.</p>
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

    /**
     * Shape CHUNG cho cả 7 mảng dịch vụ. Field nào chưa có nguồn/công thức để {@code null}
     * (successDeliveryRate, returnRate, avgDeliveryDays, các field trong {@code cod}, signal...).
     * Field đặc thù từng mảng đặt trong {@link #extra}.
     */
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

        /** Các hệ thống phát sinh giao dịch cho mảng này (distinct sourceSystem). Rỗng nếu chưa có. */
        private List<String> systemsUsed;

        // --- Metric TÍNH ĐƯỢC từ customer_events (createOrder) ---

        /** Tổng doanh thu (sum amount) trong cửa sổ xét. Với TMĐT mang ý nghĩa "Doanh thu qua sàn 12 tháng". */
        private BigDecimal totalRevenue;

        /** Tổng số đơn (count createOrder) trong cửa sổ xét. Với TMĐT mang ý nghĩa "Đơn giao chặng cuối". */
        private Long totalOrders;

        /** Trung bình số đơn / tháng = totalOrders / monthsWindow. */
        private BigDecimal avgOrdersPerMonth;

        // --- Field spec yêu cầu nhưng CHƯA có nguồn dữ liệu → null ---

        /** Tỷ lệ phát thành công. Chưa có nguồn → null. */
        private BigDecimal successDeliveryRate;

        /** Tỷ lệ hoàn. Với TMĐT mang ý nghĩa "Tỷ lệ hoàn TMĐT". Chưa có nguồn → null. */
        private BigDecimal returnRate;

        /** Thời gian giao trung bình (ngày). Chưa có nguồn → null. */
        private BigDecimal avgDeliveryDays;

        /** Thông tin COD. Null nếu mảng KHÔNG có khái niệm COD (VD Logistics, TMĐT). */
        private Cod cod;

        /** Đóng góp theo nguồn. Rỗng {@code []} nếu mảng không áp dụng. */
        private List<ContributionBySource> contributionBySource;

        /** Tín hiệu/Rủi ro. Chưa làm → null. */
        private String signal;

        /** Field đặc thù từng mảng (có thể rỗng {@code {}}). Không bao giờ null. */
        private Map<String, Object> extra;
    }

    /**
     * Khối COD. {@code total} tính được từ event (sum amount với paymentMethod=COD);
     * các field còn lại chưa có nguồn dữ liệu → null.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cod {
        private BigDecimal total;
        private BigDecimal collected;
        private BigDecimal outstanding;
        private String reconciliationStatus;
    }

    /**
     * Một dòng đóng góp theo nguồn. {@code role} chưa làm → null; {@code orderCount} và
     * {@code codContribution} tính từ event (group theo sourceSystem).
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContributionBySource {
        private String source;
        private String role;
        private Long orderCount;
        private BigDecimal codContribution;
    }
}
