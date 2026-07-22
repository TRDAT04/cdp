package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Tab "Tổng quan".
 * <p>NOTE: chưa có trường "Vai trò giao dịch (Người gửi/Chủ shop + số lần)" vì
 * hiện không có dữ liệu nguồn tương ứng trong entity/Unomi.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileOverviewResponse {

    private Long id;
    private String profileCode;

    private String fullName;
    private String gender;
    private LocalDate dateOfBirth;
    private String phone;
    private String email;

    /** CCCD đã che 1 phần. */
    private String identityNoMasked;

    /** Mã số thuế (MST) — tách khỏi CCCD. Null nếu không có. */
    private String taxCode;

    /**
     * Post ID (identity_type = POST_ID, fallback sourceSystem = POSTID).
     * @deprecated dùng {@link #identities}.postId. Giữ lại để backward-compatible.
     */
    @Deprecated
    private String postId;

    /** Toàn bộ định danh động (postId, crmId, khlCode, appUserId, deviceId, cookieId, paymentId). */
    private ProfileIdentitiesResponse identities;

    private String customerType;
    private String customerTypeText;
    /** Hạng khách hàng (VIP, FREQUENT...) — tách khỏi customerType. Null nếu không có. */
    private String customerTier;

    // Bưu cục quản lý
    private String provinceCode;
    private String provinceName;
    private String unitCode;
    private String unitName;

    /** Phân khúc hiện tại (segment) lấy từ Unomi. */
    private List<String> segments;
}
