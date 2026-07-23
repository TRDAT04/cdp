package vn.vnpost.cdp.profile.dto.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Tab "Điểm số & Phân khúc".
 *
 * <p>Tổng hợp các điểm số/khách hàng của một master profile:
 * <ul>
 *   <li>{@code rfm} — phân khúc RFM theo phương pháp phân vị CHUẨN ngành (quintile, 5 = tốt nhất)</li>
 *   <li>{@code clv} — giá trị vòng đời = tổng doanh thu 7 mảng dịch vụ (12 tháng)</li>
 *   <li>{@code churnScore} — điểm rời bỏ (0..100); {@code null} nếu khách chưa từng mua</li>
 *   <li>{@code engagementScore} — điểm gắn kết rule-based (0..100)</li>
 *   <li>{@code codRiskScore}, {@code fraudScore} — CHƯA implement → {@code null}</li>
 * </ul>
 *
 * <p>{@code @JsonInclude(ALWAYS)}: đảm bảo các field {@code null} (codRiskScore/fraudScore/churnScore)
 * VẪN xuất hiện trong JSON, kể cả khi sau này cấu hình Jackson global đổi sang NON_NULL.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ProfileScoringResponse {

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
