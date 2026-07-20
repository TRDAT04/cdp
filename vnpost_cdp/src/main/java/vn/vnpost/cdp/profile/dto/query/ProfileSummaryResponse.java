package vn.vnpost.cdp.profile.dto.query;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class ProfileSummaryResponse {

    private String fullName;
    private String uid;

    /** Nhãn phân loại: loại KH, trạng thái, v.v. */
    private List<TagItem> tags;

    /** Danh sách dịch vụ đăng ký (nếu có). */
    private List<String> serviceLines;

    /** Danh sách hệ thống nguồn đang kết nối. */
    private List<String> activeSystems;

    /** Tương tác gần nhất. */
    private LastInteraction lastInteraction;

    private Long totalOrders;
    private BigDecimal totalRevenue;
    private Integer loyaltyPoints;

    /** Độ hoàn thiện hồ sơ (0.0 – 1.0). */
    private Double profileCompleteness;

    // ----------------------------------------------------------------

    @Getter
    @Builder
    public static class TagItem {
        private String code;
        private String text;
    }

    @Getter
    @Builder
    public static class LastInteraction {
        private Instant time;
        private String channel;
    }
}
