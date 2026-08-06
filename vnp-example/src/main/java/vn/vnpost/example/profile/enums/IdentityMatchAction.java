package vn.vnpost.example.profile.enums;

/**
 * Hành động mà một rule so khớp định danh dẫn tới, ở mức người vận hành nhìn thấy.
 *
 * <p>Khác {@code MergeDecision} — enum đó là quyết định kỹ thuật của luồng ingest
 * (có 6 giá trị, gồm cả {@code CREATE_NEW_PROFILE} và {@code REJECT} vốn không phải "hành động
 * so khớp"). Enum này chỉ gom 3 nhóm hành động mà bảng rule cần hiển thị.
 */
public enum IdentityMatchAction {

    /** Hệ thống hợp nhất ngay, không cần người xác nhận. */
    AUTO_MERGE("Tự động gộp"),

    /** Gắn cờ cảnh báo ở màn đối soát định danh, chờ người đối chiếu. */
    PENDING_REVIEW("Gắn cờ chờ xác nhận"),

    /** Khớp rất yếu — không chủ động đề nghị gộp, chỉ hiện dạng gợi ý để cân nhắc. */
    LOW_CONFIDENCE("Gợi ý tin cậy thấp"),

    /**
     * Quá yếu để đề xuất — hệ thống tạo hồ sơ mới và KHÔNG gắn cờ gì.
     *
     * <p>Chỉ dùng cho mức tin cậy thấp nhất, không phải hành động của rule nào. Tồn tại vì người
     * vận hành cần biết dải điểm này có thật: dưới ngưỡng đó, hai hồ sơ nghi trùng sẽ không xuất
     * hiện ở bất kỳ hàng đợi nào.
     */
    NO_SUGGESTION("Không đề xuất");

    private final String text;

    IdentityMatchAction(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static IdentityMatchAction fromValue(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim().toUpperCase();
        for (IdentityMatchAction a : values()) {
            if (a.name().equals(key)) return a;
        }
        return null;
    }

    public static String textOf(String raw) {
        if (raw == null || raw.isBlank()) return null;
        IdentityMatchAction a = fromValue(raw);
        return a != null ? a.text : raw;
    }
}
