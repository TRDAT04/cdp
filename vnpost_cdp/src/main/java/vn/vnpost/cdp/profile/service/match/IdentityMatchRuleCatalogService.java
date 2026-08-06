package vn.vnpost.cdp.profile.service.match;

import org.springframework.stereotype.Service;
import vn.vnpost.cdp.profile.dto.match.IdentityMatchConfidenceTierResponse;
import vn.vnpost.cdp.profile.dto.match.IdentityMatchRuleCatalogResponse;
import vn.vnpost.cdp.profile.dto.match.IdentityMatchRuleResponse;
import vn.vnpost.cdp.profile.dto.match.IdentityMatchSignalWeightResponse;
import vn.vnpost.cdp.profile.enums.IdentityMatchAction;
import vn.vnpost.cdp.profile.enums.IdentityMatchRuleStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Phơi bộ rule so khớp định danh ĐANG ÁP DỤNG ra dạng dữ liệu cho FE hiển thị.
 *
 * <p><b>Chỉ đọc.</b> Rule hiện được hard-code trong Java (xem {@link IdentityMatchThresholds}), chưa
 * có trong DB, nên không có thêm/sửa/xoá/bật/tắt ở giai đoạn này.
 *
 * <p><b>Mọi con số đều lấy từ {@link IdentityMatchThresholds}</b>, không khai lại. Đây là điểm quan
 * trọng nhất của lớp này: nếu tự khai lại thì bảng hiển thị sẽ lệch khỏi hành vi thật ngay lần đổi
 * ngưỡng kế tiếp — và đúng loại lệch đó là thứ người vận hành không có cách nào tự phát hiện, họ sẽ
 * đối soát theo con số sai.
 *
 * <p><b>Vì sao 10 dòng chứ không phải 7:</b> code thật không có cấu trúc "7 rule, mỗi rule một
 * ngưỡng %". Thực tế là 6 rule tiền định (khớp tuyệt đối, KHÔNG có ngưỡng %), rồi 3 mốc điểm của
 * công thức cộng dồn, cộng một quy tắc xung đột đè lên tất cả. Bảng này mô tả đúng cấu trúc đó.
 */
@Service
public class IdentityMatchRuleCatalogService {

    private static final String PIPELINE_INGESTION = "INGESTION";
    private static final String PIPELINE_ADMIN = "ADMIN";
    private static final String PIPELINE_BOTH = "BOTH";

    private static final String WEIGHT_VERY_HIGH = "Rất cao";
    private static final String WEIGHT_HIGH = "Cao";
    private static final String WEIGHT_MEDIUM = "Trung bình";
    private static final String WEIGHT_LOW = "Thấp";

    private static final String EXACT_MATCH = "Khớp tuyệt đối";

    public IdentityMatchRuleCatalogResponse getCatalog() {
        return IdentityMatchRuleCatalogResponse.builder()
                .confidenceTiers(buildConfidenceTiers())
                .rules(buildRules())
                .signalWeights(buildSignalWeights())
                .maxScore(IdentityMatchThresholds.MAX_SCORE)
                .notes(buildNotes())
                .build();
    }

    // =====================================================================
    // Block tổng quan: "Ngưỡng độ tin cậy quyết định hành động"
    // =====================================================================

    /**
     * BỐN mức, không phải ba. Dưới mức thấp nhất còn một dải mà hệ thống hoàn toàn không đề xuất
     * gì — người vận hành cần biết dải đó tồn tại, nếu không họ sẽ tưởng mọi cặp nghi trùng đều
     * xuất hiện ở đâu đó trong hàng đợi.
     */
    private List<IdentityMatchConfidenceTierResponse> buildConfidenceTiers() {
        int autoMerge = IdentityMatchThresholds.AUTO_MERGE_SCORE;
        int pendingReview = IdentityMatchThresholds.MATCH_CANDIDATE_SCORE;
        int lowConfidence = IdentityMatchThresholds.LOW_CONFIDENCE_SCORE;

        return List.of(
                tier(1, IdentityMatchAction.AUTO_MERGE,
                        percent(autoMerge), autoMerge, null,
                        "Khóa mạnh khớp tuyệt đối — CCCD được gán "
                                + IdentityMatchThresholds.DETERMINISTIC_IDENTITY_NO + ", khóa nội bộ "
                                + IdentityMatchThresholds.DETERMINISTIC_TYPED_ID + ", MST "
                                + IdentityMatchThresholds.DETERMINISTIC_TAX_CODE
                                + " — hoặc tổng nhiều tín hiệu yếu đạt " + autoMerge
                                + ". Hệ thống hợp nhất ngay ở luồng tự động, không cần người xác nhận. "
                                + "Ngoại lệ: có xung đột khóa mạnh thì không bao giờ tự gộp."),

                tier(2, IdentityMatchAction.PENDING_REVIEW,
                        range(pendingReview, autoMerge - 1), pendingReview, autoMerge - 1,
                        "Đủ dấu hiệu nghi trùng nhưng chưa chắc — VD trùng SĐT và họ tên khớp đầy đủ ("
                                + (IdentityMatchThresholds.SCORE_PHONE
                                    + IdentityMatchThresholds.SCORE_NAME_EXACT)
                                + "đ). Gắn cờ ở màn Đối soát định danh, chờ người đối chiếu."),

                tier(3, IdentityMatchAction.LOW_CONFIDENCE,
                        range(lowConfidence, pendingReview - 1), lowConfidence, pendingReview - 1,
                        "Khớp yếu — VD chỉ trùng SĐT (" + IdentityMatchThresholds.SCORE_PHONE
                                + "đ) hoặc chỉ trùng email (" + IdentityMatchThresholds.SCORE_EMAIL
                                + "đ), vốn có thể dùng chung. Không chủ động đề nghị gộp, chỉ hiện "
                                + "dạng gợi ý tách riêng để cân nhắc."),

                tier(4, IdentityMatchAction.NO_SUGGESTION,
                        "< " + lowConfidence + "%", 0, lowConfidence - 1,
                        "Quá yếu để đề xuất — VD chỉ trùng họ tên ("
                                + IdentityMatchThresholds.SCORE_NAME_EXACT
                                + "đ), vốn rất dễ trùng với tên tiếng Việt. Hệ thống tạo hồ sơ mới và "
                                + "KHÔNG gắn cờ ở đâu cả: cặp hồ sơ này sẽ không xuất hiện trong bất "
                                + "kỳ hàng đợi đối soát nào.")
        );
    }

    // =====================================================================
    // Bảng rule — thứ tự trong list CHÍNH LÀ thứ tự ưu tiên xét trong code
    // =====================================================================

    private List<IdentityMatchRuleResponse> buildRules() {
        List<IdentityMatchRuleResponse> rules = new ArrayList<>();
        int order = 1;

        // ── Nhóm 1: khóa tiền định — khớp tuyệt đối là quyết định luôn, không qua điểm ──

        rules.add(rule(order++, "SOURCE_CUSTOMER_LINK",
                "Đã liên kết nguồn (hệ thống nguồn + mã khách tại nguồn)",
                WEIGHT_VERY_HIGH, null,
                EXACT_MATCH, null,
                IdentityMatchAction.AUTO_MERGE,
                "Hệ thống nguồn khẳng định đây là khách hàng đã biết và liên kết còn hiệu lực. "
                        + "Xét trước cả CCCD: khi khách tự sửa lại CCCD ở hệ thống nguồn thì CCCD mới "
                        + "sẽ lệch với dữ liệu đang lưu, nhưng vẫn là cùng một người.",
                PIPELINE_INGESTION, true));

        rules.add(rule(order++, "IDENTITY_NO",
                "Số CCCD/CMND",
                WEIGHT_VERY_HIGH, IdentityMatchThresholds.SCORE_IDENTITY_NO,
                percent(IdentityMatchThresholds.DETERMINISTIC_IDENTITY_NO),
                IdentityMatchThresholds.DETERMINISTIC_IDENTITY_NO,
                IdentityMatchAction.AUTO_MERGE,
                "Khớp CCCD tuyệt đối — định danh cá nhân duy nhất. So khớp bỏ qua khoảng trắng và "
                        + "phân biệt chữ hoa/thường.",
                PIPELINE_BOTH, true));

        rules.add(rule(order++, "TYPED_IDENTIFIER",
                "Khóa nội bộ do nguồn cấp: " + String.join(" / ",
                        IdentityMatchThresholds.UNIQUE_TYPED_IDENTITY_TYPES),
                WEIGHT_HIGH, null,
                percent(IdentityMatchThresholds.DETERMINISTIC_TYPED_ID),
                IdentityMatchThresholds.DETERMINISTIC_TYPED_ID,
                IdentityMatchAction.AUTO_MERGE,
                "Khớp mã định danh do hệ thống nguồn sinh ra, mỗi mã ứng với một khách hàng. "
                        + "Không dùng DEVICE_ID/COOKIE_ID vì hai người dùng chung máy sẽ bị gộp nhầm.",
                PIPELINE_BOTH, true));

        rules.add(rule(order++, "TAX_CODE_NAME_OK",
                "Mã số thuế + họ tên tương đồng ≥ "
                        + (int) IdentityMatchThresholds.NAME_SIMILARITY_MIN + "%",
                WEIGHT_VERY_HIGH, IdentityMatchThresholds.SCORE_TAX_CODE,
                percent(IdentityMatchThresholds.DETERMINISTIC_TAX_CODE),
                IdentityMatchThresholds.DETERMINISTIC_TAX_CODE,
                IdentityMatchAction.AUTO_MERGE,
                "Khớp MST — định danh doanh nghiệp duy nhất. Thiếu tên ở một bên thì coi như đạt. "
                        + "MST có gạch ngang được giữ nguyên: 0101234567-001 là chi nhánh, khác trụ sở.",
                PIPELINE_BOTH, true));

        rules.add(rule(order++, "TAX_CODE_NAME_MISMATCH",
                "Mã số thuế khớp nhưng họ tên lệch > "
                        + (100 - (int) IdentityMatchThresholds.NAME_SIMILARITY_MIN) + "%",
                WEIGHT_VERY_HIGH, IdentityMatchThresholds.SCORE_TAX_CODE,
                EXACT_MATCH, null,
                IdentityMatchAction.PENDING_REVIEW,
                "Rất có thể là hai nhân viên cùng khai MST/SĐT/email của công ty. Không tự gộp, "
                        + "cần người đối chiếu.",
                PIPELINE_BOTH, true));

        rules.add(rule(order++, "ENRICHMENT_CONTACT",
                "Event làm giàu hồ sơ + trùng SĐT hoặc email",
                WEIGHT_MEDIUM, null,
                EXACT_MATCH, null,
                IdentityMatchAction.AUTO_MERGE,
                "Chỉ áp dụng cho event PROFILE_ENRICHED — dữ liệu đến từ nguồn đã xác thực người "
                        + "dùng (app đã đăng nhập, phiên gắn email, giao dịch gắn SĐT), nên tránh tạo "
                        + "hồ sơ trùng. Không áp dụng cho event tạo/cập nhật hồ sơ thường.",
                PIPELINE_INGESTION, true));

        // ── Nhóm 2: mốc điểm của công thức cộng dồn ──

        rules.add(rule(order++, "SCORE_AUTO_MERGE",
                "Điểm tổng hợp cao (không khớp khóa mạnh nào)",
                WEIGHT_HIGH, null,
                percent(IdentityMatchThresholds.AUTO_MERGE_SCORE),
                IdentityMatchThresholds.AUTO_MERGE_SCORE,
                IdentityMatchAction.AUTO_MERGE,
                "Không khớp khóa mạnh nào, nhưng tổng nhiều tín hiệu yếu đủ cao để chắc chắn. "
                        + "Chỉ áp dụng khi không có xung đột khóa mạnh, và CHỈ ở luồng tự động — "
                        + "màn đối soát thủ công không bao giờ tự gộp.",
                PIPELINE_INGESTION, false));

        rules.add(rule(order++, "SCORE_PENDING_REVIEW",
                "Điểm tổng hợp trung bình (không khớp khóa mạnh nào)",
                WEIGHT_MEDIUM, null,
                range(IdentityMatchThresholds.MATCH_CANDIDATE_SCORE,
                        IdentityMatchThresholds.AUTO_MERGE_SCORE - 1),
                IdentityMatchThresholds.MATCH_CANDIDATE_SCORE,
                IdentityMatchAction.PENDING_REVIEW,
                "Đủ dấu hiệu để nghi trùng nhưng chưa chắc — VD trùng SĐT và họ tên khớp đầy đủ. "
                        + "Gắn cờ ở màn đối soát định danh, chờ người đối chiếu.",
                PIPELINE_BOTH, false));

        rules.add(rule(order++, "SCORE_LOW_CONFIDENCE",
                "Điểm tổng hợp thấp (không khớp khóa mạnh nào)",
                WEIGHT_LOW, null,
                range(IdentityMatchThresholds.LOW_CONFIDENCE_SCORE,
                        IdentityMatchThresholds.MATCH_CANDIDATE_SCORE - 1),
                IdentityMatchThresholds.LOW_CONFIDENCE_SCORE,
                IdentityMatchAction.LOW_CONFIDENCE,
                "Khớp yếu — VD chỉ trùng SĐT (" + IdentityMatchThresholds.SCORE_PHONE
                        + "đ) hoặc chỉ trùng email (" + IdentityMatchThresholds.SCORE_EMAIL
                        + "đ). Không chủ động đề nghị gộp, chỉ hiện dạng gợi ý để cân nhắc. "
                        + "Dưới " + IdentityMatchThresholds.LOW_CONFIDENCE_SCORE
                        + " điểm thì không tạo gợi ý: chỉ trùng họ tên ("
                        + IdentityMatchThresholds.SCORE_NAME_EXACT
                        + "đ) là quá yếu vì tên tiếng Việt rất dễ trùng.",
                PIPELINE_ADMIN, false));

        // ── Nhóm 3: quy tắc đè ──

        rules.add(rule(order, "STRONG_KEY_CONFLICT",
                "Xung đột khóa mạnh: CCCD hoặc MST hai bên khác nhau",
                WEIGHT_VERY_HIGH, null,
                "Đè mọi rule trên", null,
                IdentityMatchAction.PENDING_REVIEW,
                "Hai bên đều có CCCD (hoặc đều có MST) mà giá trị khác nhau thì không bao giờ tự "
                        + "gộp, kể cả khi mọi tín hiệu còn lại đều khớp và điểm tổng hợp đạt "
                        + IdentityMatchThresholds.MAX_SCORE + ".",
                PIPELINE_BOTH, true));

        return rules;
    }

    // =====================================================================
    // Bảng trọng số tín hiệu
    // =====================================================================

    private List<IdentityMatchSignalWeightResponse> buildSignalWeights() {
        return List.of(
                weight("IDENTITY_NO_MATCH", "CCCD/CMND",
                        IdentityMatchThresholds.SCORE_IDENTITY_NO,
                        "Định danh cá nhân duy nhất"),
                weight("TAX_CODE_MATCH", "Mã số thuế",
                        IdentityMatchThresholds.SCORE_TAX_CODE,
                        "Định danh doanh nghiệp duy nhất"),
                weight("PHONE_MATCH", "Số điện thoại",
                        IdentityMatchThresholds.SCORE_PHONE,
                        "Có thể dùng chung (người thân, shipper) nên một mình không đủ để gộp"),
                weight("EMAIL_MATCH", "Email",
                        IdentityMatchThresholds.SCORE_EMAIL,
                        "Có thể dùng chung (email công ty)"),
                weight("NAME_EXACT_MATCH", "Họ tên khớp tuyệt đối",
                        IdentityMatchThresholds.SCORE_NAME_EXACT,
                        "So sau khi bỏ dấu và chuẩn hoá khoảng trắng"),
                weight("NAME_SIMILAR_90", "Họ tên tương đồng ≥ "
                                + (int) IdentityMatchThresholds.NAME_SIMILARITY_TIER_90 + "%",
                        IdentityMatchThresholds.SCORE_NAME_SIM_90,
                        "Khác dấu hoặc sai chính tả nhẹ"),
                weight("NAME_SIMILAR_85", "Họ tên tương đồng ≥ "
                                + (int) IdentityMatchThresholds.NAME_SIMILARITY_TIER_85 + "%",
                        IdentityMatchThresholds.SCORE_NAME_SIM_85,
                        "Viết tắt hoặc thiếu một thành tố tên"),
                weight("NAME_SIMILAR_75", "Họ tên tương đồng ≥ "
                                + (int) IdentityMatchThresholds.NAME_SIMILARITY_TIER_75 + "%",
                        IdentityMatchThresholds.SCORE_NAME_SIM_75,
                        "Lệch khá nhiều — điểm thấp, chỉ mang tính hỗ trợ"),
                weight("DATE_OF_BIRTH_MATCH", "Ngày sinh",
                        IdentityMatchThresholds.SCORE_DOB,
                        "Tín hiệu phụ, tăng độ tin cậy khi kết hợp tên"),
                weight("PROVINCE_MATCH", "Tỉnh/Thành phố",
                        IdentityMatchThresholds.SCORE_PROVINCE,
                        "Tín hiệu yếu, chỉ dùng để phá thế cân bằng"),
                weight("UNIT_MATCH", "Bưu cục",
                        IdentityMatchThresholds.SCORE_UNIT,
                        "Tín hiệu yếu nhất")
        );
    }

    private List<String> buildNotes() {
        return List.of(
                "Cột Hành động mô tả luồng TỰ ĐỘNG (khi hệ thống nhận dữ liệu từ nguồn). Ở màn đối "
                        + "soát định danh thủ công, hệ thống KHÔNG bao giờ tự gộp — mọi cặp hồ sơ đều "
                        + "chờ người bấm xác nhận; các ngưỡng ở đó chỉ dùng để tính điểm và xếp thứ "
                        + "tự ưu tiên xử lý.",
                "Rule được xét theo đúng thứ tự trên: khóa mạnh xét trước, điểm cộng dồn chỉ là "
                        + "phương án dự phòng khi không có khóa mạnh nào so được cả hai bên.",
                "Điểm cộng dồn bị giới hạn ở " + IdentityMatchThresholds.MAX_SCORE + ".",
                "Khi có nhiều hồ sơ ứng viên, hệ thống thử tách bằng khóa mạnh trước (liên kết nguồn "
                        + "→ CCCD → MST → khóa nội bộ). Chỉ khi không tách được, hoặc có từ hai hồ sơ "
                        + "cùng khớp một khóa mạnh, thì mới báo xung đột chờ xử lý.",
                "Một giá trị khóa trùng với hơn " + IdentityMatchThresholds.MAX_PROFILES_PER_KEY
                        + " hồ sơ được coi là khóa rác (VD số hotline, 0000000000) và bị bỏ qua để "
                        + "không sinh hàng loạt gợi ý nhiễu.",
                "Ứng viên bị bỏ qua hoặc từ chối chỉ được đề xuất lại nếu điểm mới cao hơn ít nhất "
                        + IdentityMatchThresholds.RECREATE_SCORE_IMPROVEMENT + " điểm.",
                "Nhóm hồ sơ có ứng viên dưới " + IdentityMatchThresholds.LOW_CONFIDENCE_FLAG_SCORE
                        + " điểm được gắn cờ tin cậy thấp trên màn đối soát.",
                "Việc thêm/sửa/tắt rule chưa được hỗ trợ — rule hiện cố định trong hệ thống."
        );
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private IdentityMatchRuleResponse rule(int order, String code, String matchKey,
                                           String weightLabel, Integer weightScore,
                                           String confidenceThreshold, Integer thresholdValue,
                                           IdentityMatchAction action, String description,
                                           String pipeline, boolean deterministic) {
        return IdentityMatchRuleResponse.builder()
                .order(order)
                .code(code)
                .matchKey(matchKey)
                .weightLabel(weightLabel)
                .weightScore(weightScore)
                .confidenceThreshold(confidenceThreshold)
                .thresholdValue(thresholdValue)
                .action(action.name())
                .actionText(action.getText())
                .description(description)
                .status(IdentityMatchRuleStatus.ACTIVE.name())
                .statusText(IdentityMatchRuleStatus.ACTIVE.getText())
                .pipeline(pipeline)
                .deterministic(deterministic)
                .build();
    }

    private IdentityMatchConfidenceTierResponse tier(int order, IdentityMatchAction action,
                                                     String range, Integer fromScore,
                                                     Integer toScore, String description) {
        return IdentityMatchConfidenceTierResponse.builder()
                .order(order)
                .code(action.name())
                .range(range)
                .fromScore(fromScore)
                .toScore(toScore)
                .action(action.name())
                .actionText(action.getText())
                .description(description)
                .build();
    }

    private IdentityMatchSignalWeightResponse weight(String code, String signal, int score,
                                                     String description) {
        return IdentityMatchSignalWeightResponse.builder()
                .code(code)
                .signal(signal)
                .score(score)
                .description(description)
                .build();
    }

    private String percent(int value) {
        return "≥ " + value + "%";
    }

    private String range(int from, int to) {
        return from + "–" + to + "%";
    }
}
