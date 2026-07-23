package vn.vnpost.cdp.profile.enums;


public enum CustomerGroup {

    /** Cá nhân thường (thuộc {@link CustomerType#PERSONAL}). */
    PERSONAL_NORMAL("Cá nhân thường"),
    /** Doanh nghiệp / chủ shop nhỏ (thuộc {@link CustomerType#BUSINESS}). */
    SME("SME"),
    /** Khách hàng lớn — doanh nghiệp/TMĐT có sản lượng lớn (thuộc {@link CustomerType#BUSINESS}). */
    KHL("Khách hàng lớn");

    private final String text;

    CustomerGroup(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }


    public static CustomerGroup fromValue(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = normalizeKey(raw);
        for (CustomerGroup g : values()) {
            if (g.name().equals(key)) return g;
        }
        return null;
    }

    public static String textOf(String raw) {
        if (raw == null || raw.isBlank()) return null;
        CustomerGroup g = fromValue(raw);
        return g != null ? g.text : raw;
    }

    private static String normalizeKey(String raw) {
        String key = raw.trim().toUpperCase();
        // Alias tiếng Việt / giá trị cũ.
        return switch (key) {
            case "CA_NHAN_THUONG", "PERSONAL", "CA_NHAN" -> "PERSONAL_NORMAL";
            case "CHU_SHOP", "SHOP_OWNER" -> "SME";
            case "KHACH_HANG_LON", "TMDT" -> "KHL";
            default -> key;
        };
    }
}
