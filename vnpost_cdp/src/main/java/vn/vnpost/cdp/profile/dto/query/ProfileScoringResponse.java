package vn.vnpost.cdp.profile.dto.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ProfileScoringResponse {
    private LocalDateTime calculatedAt;
    private Rfm rfm;

    /** Giá trị vòng đời khách hàng = SUM(totalRevenue) của 7 mảng dịch vụ. */
    private BigDecimal clv;

    /** Điểm rời bỏ 0..100. {@code null} khi khách chưa có đơn hàng nào. */
    private Integer churnScore;

    /** Điểm gắn kết 0..100 (rule-based). */
    private Integer engagementScore;

    /** Rủi ro COD — chưa implement (chưa join được complaint↔order) → {@code null}. */
    private Integer codRiskScore;

    /** Điểm gian lận — chưa implement (cần đối chiếu pattern nhiều khách) → {@code null}. */
    private Integer fraudScore;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Rfm {
        /** Tên nhóm: Champions / Loyal Customers / New Customers / At Risk / Lost / Big Spenders / Regular. */
        private String segment;
        /** Điểm Recency 1..5 (5 = mua gần đây nhất = tốt nhất). */
        private Integer recencyScore;
        /** Điểm Frequency 1..5 (5 = mua nhiều nhất). */
        private Integer frequencyScore;
        /** Điểm Monetary 1..5 (5 = chi tiêu cao nhất). */
        private Integer monetaryScore;
    }
}
