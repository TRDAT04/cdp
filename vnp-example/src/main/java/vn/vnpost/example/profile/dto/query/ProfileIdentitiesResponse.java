package vn.vnpost.example.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gom nhóm toàn bộ định danh động của một profile (lấy từ {@code profile_identity_links}).
 *
 * <p>Mỗi field là giá trị định danh gần nhất theo {@code identity_type} tương ứng.
 * Field nào profile chưa từng ghi nhận sẽ để {@code null} (không bỏ field, không trả chuỗi rỗng).</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileIdentitiesResponse {

    /** identity_type = POST_ID (fallback: sourceSystem = POSTID). */
    private String postId;

    /** identity_type = CRM_ID. */
    private String crmId;

    /** identity_type = KHL_CODE (mã khách hàng lớn). */
    private String khlCode;

    /** identity_type = APP_USER_ID (user id trên app MyVNPost). */
    private String appUserId;

    /** identity_type = DEVICE_ID. */
    private String deviceId;

    /** identity_type = COOKIE_ID. */
    private String cookieId;

    /** identity_type = PAYMENT_ID (PayPost...). */
    private String paymentId;
}
