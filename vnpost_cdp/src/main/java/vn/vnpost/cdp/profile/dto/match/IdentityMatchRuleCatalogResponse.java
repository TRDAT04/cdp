package vn.vnpost.cdp.profile.dto.match;

import lombok.*;

import java.util.List;

/**
 * Toàn bộ dữ liệu cho màn "Danh sách rule so khớp định danh".
 *
 * <p>Gói cả bảng rule và bảng trọng số vào một response để FE dựng màn bằng một lần gọi — hai bảng
 * này luôn được xem cùng nhau và đều là dữ liệu tĩnh, tách thành hai endpoint chỉ thêm round-trip.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityMatchRuleCatalogResponse {

    /**
     * Block tổng quan hiển thị PHÍA TRÊN bảng rule: "Ngưỡng độ tin cậy quyết định hành động".
     * Trả lời "điểm bao nhiêu thì hệ thống làm gì", trước khi người đọc vào chi tiết từng rule.
     */
    private List<IdentityMatchConfidenceTierResponse> confidenceTiers;

    /** Bảng chính: các rule theo thứ tự ưu tiên xét, khóa mạnh trước. */
    private List<IdentityMatchRuleResponse> rules;

    /** Bảng phụ: trọng số từng tín hiệu trong công thức điểm cộng dồn. */
    private List<IdentityMatchSignalWeightResponse> signalWeights;

    /** Điểm tối đa của công thức cộng dồn (điểm bị cap ở mức này). */
    private Integer maxScore;

    /**
     * Ghi chú áp dụng xuyên suốt, không thuộc riêng dòng nào — FE nên hiển thị dưới bảng.
     * VD quy tắc xung đột khóa mạnh đè mọi rule khác.
     */
    private List<String> notes;
}
