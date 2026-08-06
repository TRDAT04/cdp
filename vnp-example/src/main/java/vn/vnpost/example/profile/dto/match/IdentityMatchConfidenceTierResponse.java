package vn.vnpost.example.profile.dto.match;

import lombok.*;

/**
 * Một mức của bảng "Ngưỡng độ tin cậy quyết định hành động" — block tổng quan hiển thị PHÍA TRÊN
 * danh sách rule.
 *
 * <p>Khác {@link IdentityMatchRuleResponse}: DTO đó mô tả từng rule cụ thể (khóa nào, điểm bao
 * nhiêu), còn DTO này chỉ trả lời câu hỏi "điểm bao nhiêu thì hệ thống làm gì" — dùng để người
 * vận hành nắm nguyên tắc chung trước khi đọc chi tiết.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityMatchConfidenceTierResponse {

    /** Thứ tự hiển thị, từ mức tin cậy cao xuống thấp. */
    private Integer order;

    /** Mã mức — trùng tên enum {@code IdentityMatchAction} của hành động tương ứng. */
    private String code;

    /** Dải điểm dạng hiển thị: "≥ 95%", "70–94%", "35–69%", "< 35%". */
    private String range;

    /** Điểm thấp nhất thuộc mức này (bao gồm). */
    private Integer fromScore;

    /** Điểm cao nhất thuộc mức này (bao gồm). {@code null} với mức cao nhất (không có trần). */
    private Integer toScore;

    /** Hành động của mức này — tên enum {@code IdentityMatchAction}. */
    private String action;
    private String actionText;

    /** Diễn giải: loại khóa điển hình rơi vào mức này và hệ quả. */
    private String description;
}
