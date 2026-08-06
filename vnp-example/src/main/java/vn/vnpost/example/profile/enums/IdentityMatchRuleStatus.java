package vn.vnpost.example.profile.enums;

/**
 * Trạng thái áp dụng của một rule so khớp định danh.
 *
 * <p>Hiện tại rule được hard-code trong Java nên mọi rule đều {@code ACTIVE} — không có cách nào
 * tắt một rule mà không sửa code. Enum này tồn tại để giai đoạn sau (khi rule được đưa vào DB và
 * có màn bật/tắt) FE không phải đổi contract: field đã có sẵn, chỉ giá trị thay đổi.
 */
public enum IdentityMatchRuleStatus {

    /** Đang áp dụng — rule có hiệu lực trong luồng so khớp. */
    ACTIVE("Đang áp dụng"),

    /** Tạm dừng — rule tồn tại nhưng không tham gia so khớp. Chưa dùng ở giai đoạn này. */
    INACTIVE("Tạm dừng");

    private final String text;

    IdentityMatchRuleStatus(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static IdentityMatchRuleStatus fromValue(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim().toUpperCase();
        for (IdentityMatchRuleStatus s : values()) {
            if (s.name().equals(key)) return s;
        }
        return null;
    }

    public static String textOf(String raw) {
        if (raw == null || raw.isBlank()) return null;
        IdentityMatchRuleStatus s = fromValue(raw);
        return s != null ? s.text : raw;
    }
}
