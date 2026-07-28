package vn.vnpost.example.profile.enums;

/**
 * Danh mục CHUẨN cho {@code profile_identity_links.identity_type}.
 *
 * <p>Cột {@code identity_type} là VARCHAR tự do trong DB; enum này đóng vai trò
 * "danh mục" phía ứng dụng để tránh sai chính tả và để chuẩn hóa việc match/tra cứu.</p>
 *
 * <ul>
 *   <li>Nhóm định danh mạnh (đã dùng từ trước): {@link #IDENTITY_NO}, {@link #PHONE},
 *       {@link #EMAIL}, {@link #SOURCE_CUSTOMER_ID}.</li>
 *   <li>Nhóm định danh liên nguồn (bổ sung): {@link #POST_ID}, {@link #CRM_ID},
 *       {@link #KHL_CODE}, {@link #APP_USER_ID}, {@link #DEVICE_ID},
 *       {@link #COOKIE_ID}, {@link #PAYMENT_ID}.</li>
 * </ul>
 */
public enum IdentityType {

    // --- Định danh mạnh (dùng để match/dedup profile) ---
    IDENTITY_NO,
    PHONE,
    EMAIL,
    SOURCE_CUSTOMER_ID,

    // --- Định danh liên nguồn (enrichment) ---
    POST_ID,
    CRM_ID,
    KHL_CODE,
    APP_USER_ID,
    DEVICE_ID,
    COOKIE_ID,
    PAYMENT_ID;

    public static boolean isValid(String code) {
        if (code == null) return false;
        for (IdentityType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return true;
        }
        return false;
    }
}
