package vn.vnpost.example.profile.service.match;

import java.util.List;

/**
 * Toàn bộ con số của bộ rule so khớp định danh — nơi duy nhất được phép định nghĩa chúng.
 *
 * <p>Lý do tồn tại: các hằng số này vừa điều khiển hành vi thật (scorer, decision service), vừa
 * được phơi ra API cho FE hiển thị bảng rule. Nếu API tự khai lại con số thì bảng hiển thị sẽ lệch
 * khỏi hành vi hệ thống ngay lần đổi ngưỡng kế tiếp — mà đúng loại lệch đó là thứ người vận hành
 * không có cách nào phát hiện. Đổi ngưỡng thì đổi ở đây, cả hai phía tự khớp.
 *
 * <p>Xem {@code vnpost_cdp/docs/profile-ingestion-data-flow.md} mục 5 để biết vì sao chọn các mốc
 * này — tài liệu nằm ở project gốc, bộ rule ở hai project phải giữ cùng con số.
 */
public final class IdentityMatchThresholds {

    private IdentityMatchThresholds() {
    }

    /**
     * Các loại {@code identity_link} được coi là khóa duy nhất do hệ thống nguồn cấp.
     *
     * <p>Cố tình KHÔNG gồm {@code DEVICE_ID} / {@code COOKIE_ID} — hai loại đó để dành cho
     * Probabilistic Matching sau này; dùng chúng để auto-merge sẽ gộp nhầm hai người dùng chung máy.
     */
    public static final List<String> UNIQUE_TYPED_IDENTITY_TYPES =
            List.of("KHL_CODE", "CRM_ID", "POST_ID", "APP_USER_ID", "PAYMENT_ID");

    // =====================================================================
    // Trọng số của công thức điểm cộng dồn (probabilistic)
    // =====================================================================

    public static final int SCORE_IDENTITY_NO = 50;
    public static final int SCORE_TAX_CODE = 50;
    public static final int SCORE_PHONE = 40;
    public static final int SCORE_EMAIL = 35;
    public static final int SCORE_NAME_EXACT = 30;
    public static final int SCORE_NAME_SIM_90 = 20;
    public static final int SCORE_NAME_SIM_85 = 15;
    public static final int SCORE_NAME_SIM_75 = 5;
    public static final int SCORE_DOB = 20;
    public static final int SCORE_PROVINCE = 10;
    public static final int SCORE_UNIT = 5;

    /** Điểm cộng dồn bị cap ở mức này. */
    public static final int MAX_SCORE = 100;

    // =====================================================================
    // Mốc quyết định hành động
    // =====================================================================

    /** Từ mốc này trở lên và không có xung đột khóa mạnh → tự động gộp. */
    public static final int AUTO_MERGE_SCORE = 95;

    /** Từ mốc này trở lên → gắn cờ chờ xác nhận (luồng ingest tạo match candidate ngay). */
    public static final int MATCH_CANDIDATE_SCORE = 70;

    /**
     * Sàn tạo candidate ở luồng admin/detect → gợi ý tin cậy thấp.
     *
     * <p>35 bắt được "chỉ trùng SĐT" (40đ) và "chỉ trùng email" (35đ), nhưng cố ý loại
     * "chỉ trùng tên" (30đ) vì tên tiếng Việt quá dễ trùng nên sẽ sinh gợi ý nhiễu.
     */
    public static final int LOW_CONFIDENCE_SCORE = 35;

    /** Nhóm có candidate dưới mốc này thì UI gắn cờ "tin cậy thấp" để cảnh báo. */
    public static final int LOW_CONFIDENCE_FLAG_SCORE = 60;

    // =====================================================================
    // Điểm gán cho cặp khớp khóa mạnh (deterministic)
    // =====================================================================
    // Cả ba đều KHÔNG phải bội số của 5, trong khi mọi trọng số additive ở trên đều là bội số của 5
    // → điểm cộng dồn luôn là bội số của 5. Nhờ vậy nhìn score là biết candidate đến từ nhánh
    // deterministic hay additive. Đừng đổi sang bội số của 5 (VD 95) vì sẽ phá vỡ tính chất này.

    public static final int DETERMINISTIC_IDENTITY_NO = 98;
    public static final int DETERMINISTIC_TYPED_ID = 97;
    public static final int DETERMINISTIC_TAX_CODE = 96;

    // =====================================================================
    // Ngưỡng phụ trợ
    // =====================================================================

    /** Tên hai bên "không lệch quá xa" — dùng để không phủ định một khóa mạnh đã khớp. */
    public static final double NAME_SIMILARITY_MIN = 75;

    public static final double NAME_SIMILARITY_TIER_90 = 90;
    public static final double NAME_SIMILARITY_TIER_85 = 85;
    public static final double NAME_SIMILARITY_TIER_75 = 75;

    /** Mốc phân loại matchLevel hiển thị trên UI. */
    public static final int LEVEL_VERY_HIGH = 95;
    public static final int LEVEL_HIGH = 85;
    public static final int LEVEL_MEDIUM = 70;

    /**
     * Một giá trị khóa khớp nhiều hơn ngưỡng này thì coi như khóa rác (SĐT hotline shipper,
     * "0000000000") và không dùng để ghép cặp.
     */
    public static final int MAX_PROFILES_PER_KEY = 20;

    /** Candidate từng bị IGNORED/REJECTED chỉ được tạo lại nếu điểm mới cao hơn ít nhất mức này. */
    public static final int RECREATE_SCORE_IMPROVEMENT = 10;
}
