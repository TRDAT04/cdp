package vn.vnpost.example.profile.enums;


public enum CustomerType {

    /** Cá nhân (= Individual Customer). */
    PERSONAL("Cá nhân"),
    /** Doanh nghiệp (= Business Customer). */
    BUSINESS("Doanh nghiệp");

    private final String text;

    CustomerType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }


    public static CustomerType fromValue(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = normalizeKey(raw);
        for (CustomerType t : values()) {
            if (t.name().equals(key)) return t;
        }
        return null;
    }

    public static String textOf(String raw) {
        if (raw == null || raw.isBlank()) return null;
        CustomerType t = fromValue(raw);
        return t != null ? t.text : raw;
    }

    private static String normalizeKey(String raw) {
        String key = raw.trim().toUpperCase();
        return switch (key) {
            case "CA_NHAN" -> "PERSONAL";
            case "DOANH_NGHIEP", "ENTERPRISE" -> "BUSINESS";
            default -> key;
        };
    }
}
