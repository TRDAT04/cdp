package vn.vnpost.cdp.profile.dto.match;

import lombok.*;

/**
 * Một dòng của bảng trọng số tín hiệu — chi tiết bên trong công thức điểm cộng dồn.
 *
 * <p>Không phải "rule", mà là thành phần cấu tạo nên điểm của các rule dựa trên điểm
 * (dòng 7/8/9 của bảng rule). Dùng cho popup/panel giải thích "vì sao cặp này được N điểm".
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityMatchSignalWeightResponse {

    /** Mã tín hiệu, khớp với {@code reason_type} lưu ở bảng {@code profile_match_reasons}. */
    private String code;

    /** Nhãn hiển thị, VD "CCCD/CMND", "SĐT", "Tên gần đúng (≥90%)". */
    private String signal;

    /** Điểm cộng khi tín hiệu này khớp. */
    private Integer score;

    /** Giải thích ngắn. */
    private String description;
}
