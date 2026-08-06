package vn.vnpost.cdp.profile.dto.match;

import lombok.*;

/**
 * Một dòng của bảng "Danh sách rule so khớp định danh".
 *
 * <p>Mỗi field text (`...Text`) là chuỗi hiển thị sẵn để FE bind trực tiếp; field thô đi kèm
 * (`action`, `status`, `thresholdValue`, `weightScore`) để FE lọc/sắp xếp/tô màu mà không phải
 * parse chuỗi tiếng Việt.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityMatchRuleResponse {

    /** Số thứ tự hiển thị, cũng chính là thứ tự ưu tiên xét rule (nhỏ xét trước). */
    private Integer order;

    /** Mã rule, bền vững qua các lần đổi nhãn — FE nên tham chiếu mã này chứ không phải `matchKey`. */
    private String code;

    /** Cột "Khóa khớp". */
    private String matchKey;

    /** Cột "Trọng số" — nhãn định tính: Rất cao / Cao / Trung bình / Thấp / Rất thấp. */
    private String weightLabel;

    /**
     * Điểm của khóa này trong công thức điểm cộng dồn.
     * {@code null} với rule tiền định (khớp tuyệt đối, không qua điểm) và với rule là mốc điểm.
     */
    private Integer weightScore;

    /** Cột "Ngưỡng tin cậy" — VD "98%", "70–94%", "Khớp tuyệt đối". */
    private String confidenceThreshold;

    /**
     * Giá trị số của ngưỡng, để FE so sánh/sắp xếp.
     * {@code null} khi rule không dựa trên ngưỡng phần trăm.
     */
    private Integer thresholdValue;

    /** Cột "Hành động" — tên enum {@code IdentityMatchAction}. */
    private String action;
    private String actionText;

    /** Cột "Diễn giải". */
    private String description;

    /** Cột "Trạng thái" — tên enum {@code IdentityMatchRuleStatus}. */
    private String status;
    private String statusText;

    /**
     * Luồng áp dụng rule: {@code INGESTION} (tự động khi nhận dữ liệu), {@code ADMIN}
     * (đối soát thủ công), {@code BOTH}. Một số rule chỉ có ở một luồng — FE có thể bỏ qua field
     * này, nhưng nó cần thiết để giải thích vì sao cùng một cặp hồ sơ lại được xử lý khác nhau.
     */
    private String pipeline;

    /**
     * {@code true} nếu rule là khóa tiền định (khớp tuyệt đối là quyết định luôn), {@code false}
     * nếu rule dựa trên điểm cộng dồn. FE có thể dùng để chia nhóm hiển thị.
     */
    private Boolean deterministic;
}
