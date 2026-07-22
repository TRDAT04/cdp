package vn.vnpost.cdp.profile.enums;

/**
 * Loại khách hàng theo nghiệp vụ CDP.
 *
 * <p>Đây là danh mục CHUẨN cho cột {@code master_profiles.customer_type}.
 * Các giá trị "hạng khách" như VIP / FREQUENT KHÔNG thuộc enum này — chúng
 * được tách sang {@code master_profiles.customer_tier} (xem migration).</p>
 */
public enum CustomerType {

    PERSONAL("Cá nhân"),
    BUSINESS("Doanh nghiệp"),
    KHL("Khách hàng lớn"),
    SHOP_OWNER("Chủ shop"),
    TMDT("Thương mại điện tử");

    private final String text;

    CustomerType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    /**
     * Quy đổi giá trị lưu trong DB sang enum (tolerant).
     *
     * @return enum khớp, hoặc {@code null} nếu không khớp giá trị chuẩn nào.
     */
    public static CustomerType fromValue(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = normalizeKey(raw);
        for (CustomerType t : values()) {
            if (t.name().equals(key)) return t;
        }
        return null;
    }

    /**
     * Nhãn hiển thị tiếng Việt. Với giá trị legacy/không chuẩn (VIP, FREQUENT...)
     * trả về nguyên văn để không mất thông tin trên UI trước khi dữ liệu được migrate.
     */
    public static String textOf(String raw) {
        if (raw == null || raw.isBlank()) return null;
        CustomerType t = fromValue(raw);
        return t != null ? t.text : raw;
    }

    private static String normalizeKey(String raw) {
        String key = raw.trim().toUpperCase();
        // Alias tiếng Việt cũ
        return switch (key) {
            case "CA_NHAN" -> "PERSONAL";
            case "DOANH_NGHIEP" -> "BUSINESS";
            case "CHU_SHOP" -> "SHOP_OWNER";
            default -> key;
        };
    }
}
