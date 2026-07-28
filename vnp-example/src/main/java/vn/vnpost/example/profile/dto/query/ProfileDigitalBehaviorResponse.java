package vn.vnpost.example.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tab "Hành vi số".
 * <p>
 * Toàn bộ dữ liệu suy diễn từ {@code customer_events} (hành vi chi tiết: đăng nhập,
 * đơn hàng, timeline). Segment và số liệu tổng mua đã hiển thị ở tab Tổng quan/Summary.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDigitalBehaviorResponse {

    /** Lần đăng nhập gần nhất (event {@code customerLogin}). */
    private LocalDateTime lastLoginAt;

    /** Thiết bị đăng nhập gần nhất — để trống tới khi chốt nguồn dữ liệu. */
    private String device;

    /** Số phiên trong 30 ngày — để trống tới khi chốt công thức. */
    private Integer sessionsLast30Days;

    /** Điểm gắn kết — để trống tới khi chốt công thức. */
    private Integer engagementScore;

    /** Các kênh/hệ thống đã phát sinh tương tác (distinct sourceSystem). */
    private List<String> channelsInteracted;

    /** Đơn hàng gần nhất (event {@code createOrder}). */
    private RecentOrder recentOrder;

    /** Dòng thời gian các event gần đây (mới nhất trước). */
    private List<TimelineItem> timeline;

    /**
     * Lần phản hồi campaign gần nhất (event {@code campaignResponse}).
     * Null nếu profile chưa có event phản hồi campaign nào.
     */
    private LastCampaignResponse lastCampaignResponse;

    // ----------------------------------------------------------------

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LastCampaignResponse {
        /** Mã campaign (VD: SALE06). */
        private String campaignCode;
        /** Kênh phản hồi (VD: EMAIL) — property {@code channel}, fallback sourceSystem. */
        private String channel;
        private LocalDateTime occurredAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentOrder {
        private String orderId;
        private BigDecimal amount;
        private String serviceCode;
        private String orderStatus;
        private LocalDateTime occurredAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineItem {
        private String eventType;
        private String eventTypeText;
        private String sourceSystem;
        private LocalDateTime occurredAt;
        /** Mô tả ngắn (vd: mã đơn / số tiền) nếu suy diễn được từ properties. */
        private String summary;
    }
}
