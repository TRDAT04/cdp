package vn.vnpost.cdp.profile.service.match;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.vnpost.cdp.profile.dto.match.IdentityMatchConfidenceTierResponse;
import vn.vnpost.cdp.profile.dto.match.IdentityMatchRuleCatalogResponse;
import vn.vnpost.cdp.profile.dto.match.IdentityMatchRuleResponse;
import vn.vnpost.cdp.profile.enums.IdentityMatchAction;
import vn.vnpost.cdp.profile.enums.IdentityMatchRuleStatus;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chốt rằng bảng rule phơi cho FE **khớp với hằng số điều khiển hành vi thật**.
 *
 * <p>Đây là lý do tồn tại của cả cách làm này: nếu bảng hiển thị lệch khỏi hành vi hệ thống, người
 * vận hành sẽ đối soát theo con số sai mà không có cách nào tự phát hiện. Các test dưới đây so
 * trực tiếp giá trị trong response với {@link IdentityMatchThresholds}, nên đổi ngưỡng mà quên cập
 * nhật một chỗ là test đỏ ngay.
 */
class IdentityMatchRuleCatalogServiceTest {

    private final IdentityMatchRuleCatalogService service = new IdentityMatchRuleCatalogService();

    private Map<String, IdentityMatchRuleResponse> rulesByCode() {
        return service.getCatalog().getRules().stream()
                .collect(Collectors.toMap(IdentityMatchRuleResponse::getCode, Function.identity()));
    }

    @Test
    @DisplayName("Trả đúng 10 rule")
    void catalogHasTenRules() {
        assertThat(service.getCatalog().getRules()).hasSize(10);
    }

    // =====================================================================
    // Block "Ngưỡng độ tin cậy quyết định hành động"
    // =====================================================================

    @Test
    @DisplayName("Có 4 mức tin cậy, không phải 3 — dải dưới sàn đề xuất cũng phải hiện")
    void confidenceTiersHaveFourLevels() {
        // Mức thứ 4 (< sàn đề xuất) là dải mà hệ thống không gắn cờ ở đâu cả. Bỏ nó khỏi bảng thì
        // người vận hành sẽ tưởng mọi cặp nghi trùng đều nằm đâu đó trong hàng đợi.
        assertThat(service.getCatalog().getConfidenceTiers()).hasSize(4);
    }

    @Test
    @DisplayName("Các mức tin cậy liền nhau: không hở điểm, không chồng lấn")
    void confidenceTiersAreContiguousWithNoGapOrOverlap() {
        List<IdentityMatchConfidenceTierResponse> tiers = service.getCatalog().getConfidenceTiers();

        // Mức 1 không có trần; các mức sau phải nối đúng: from của mức trên = to của mức dưới + 1.
        assertThat(tiers.get(0).getToScore()).isNull();
        for (int i = 0; i < tiers.size() - 1; i++) {
            Integer upperFrom = tiers.get(i).getFromScore();
            Integer lowerTo = tiers.get(i + 1).getToScore();
            assertThat(lowerTo)
                    .as("mức %d phải nối khít mức %d", i + 2, i + 1)
                    .isEqualTo(upperFrom - 1);
        }
        // Mức thấp nhất phải chạm 0, nếu không có dải điểm nào không được mô tả.
        assertThat(tiers.get(tiers.size() - 1).getFromScore()).isZero();
    }

    @Test
    @DisplayName("Mốc của các mức tin cậy khớp đúng hằng số quyết định")
    void confidenceTierBoundariesMatchConstants() {
        List<IdentityMatchConfidenceTierResponse> tiers = service.getCatalog().getConfidenceTiers();

        assertThat(tiers.get(0).getFromScore()).isEqualTo(IdentityMatchThresholds.AUTO_MERGE_SCORE);
        assertThat(tiers.get(1).getFromScore()).isEqualTo(IdentityMatchThresholds.MATCH_CANDIDATE_SCORE);
        assertThat(tiers.get(2).getFromScore()).isEqualTo(IdentityMatchThresholds.LOW_CONFIDENCE_SCORE);
    }

    @Test
    @DisplayName("Mức tin cậy sắp từ cao xuống thấp")
    void confidenceTiersAreOrderedFromHighToLow() {
        List<IdentityMatchConfidenceTierResponse> tiers = service.getCatalog().getConfidenceTiers();

        for (int i = 0; i < tiers.size(); i++) {
            assertThat(tiers.get(i).getOrder()).isEqualTo(i + 1);
        }
        for (int i = 0; i < tiers.size() - 1; i++) {
            assertThat(tiers.get(i).getFromScore())
                    .isGreaterThan(tiers.get(i + 1).getFromScore());
        }
    }

    @Test
    @DisplayName("Ba mức đầu khớp đúng hành động của ba rule mốc điểm trong bảng dưới")
    void confidenceTierActionsMatchScoreBandRules() {
        // Nếu block tổng quan và bảng chi tiết nói khác nhau thì người đọc không biết tin cái nào.
        Map<String, IdentityMatchRuleResponse> byCode = rulesByCode();
        List<IdentityMatchConfidenceTierResponse> tiers = service.getCatalog().getConfidenceTiers();

        assertThat(tiers.get(0).getAction()).isEqualTo(byCode.get("SCORE_AUTO_MERGE").getAction());
        assertThat(tiers.get(1).getAction()).isEqualTo(byCode.get("SCORE_PENDING_REVIEW").getAction());
        assertThat(tiers.get(2).getAction()).isEqualTo(byCode.get("SCORE_LOW_CONFIDENCE").getAction());

        assertThat(tiers.get(0).getFromScore()).isEqualTo(byCode.get("SCORE_AUTO_MERGE").getThresholdValue());
        assertThat(tiers.get(1).getFromScore()).isEqualTo(byCode.get("SCORE_PENDING_REVIEW").getThresholdValue());
        assertThat(tiers.get(2).getFromScore()).isEqualTo(byCode.get("SCORE_LOW_CONFIDENCE").getThresholdValue());
    }

    @Test
    @DisplayName("Mức thấp nhất nói rõ hệ thống không gắn cờ ở đâu cả")
    void lowestTierStatesNothingIsFlagged() {
        List<IdentityMatchConfidenceTierResponse> tiers = service.getCatalog().getConfidenceTiers();
        IdentityMatchConfidenceTierResponse lowest = tiers.get(tiers.size() - 1);

        assertThat(lowest.getAction()).isEqualTo(IdentityMatchAction.NO_SUGGESTION.name());
        assertThat(lowest.getDescription()).contains("KHÔNG gắn cờ");
    }

    @Test
    @DisplayName("Mọi mức tin cậy đều có đủ text hiển thị")
    void everyConfidenceTierIsFullyPopulated() {
        assertThat(service.getCatalog().getConfidenceTiers()).allSatisfy(t -> {
            assertThat(t.getRange()).isNotBlank();
            assertThat(t.getDescription()).isNotBlank();
            assertThat(IdentityMatchAction.fromValue(t.getAction())).isNotNull();
            assertThat(t.getActionText())
                    .isEqualTo(IdentityMatchAction.fromValue(t.getAction()).getText());
        });
    }

    @Test
    @DisplayName("Mã rule không trùng nhau — FE tham chiếu theo mã")
    void ruleCodesAreUnique() {
        List<String> codes = service.getCatalog().getRules().stream()
                .map(IdentityMatchRuleResponse::getCode)
                .toList();

        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Số thứ tự liên tục 1..10 và chính là thứ tự trong danh sách")
    void ordersAreSequentialAndMatchListOrder() {
        List<IdentityMatchRuleResponse> rules = service.getCatalog().getRules();

        for (int i = 0; i < rules.size(); i++) {
            assertThat(rules.get(i).getOrder()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("Ngưỡng của rule khóa mạnh khớp đúng hằng số deterministic")
    void deterministicThresholdsMatchConstants() {
        Map<String, IdentityMatchRuleResponse> byCode = rulesByCode();

        assertThat(byCode.get("IDENTITY_NO").getThresholdValue())
                .isEqualTo(IdentityMatchThresholds.DETERMINISTIC_IDENTITY_NO);
        assertThat(byCode.get("TYPED_IDENTIFIER").getThresholdValue())
                .isEqualTo(IdentityMatchThresholds.DETERMINISTIC_TYPED_ID);
        assertThat(byCode.get("TAX_CODE_NAME_OK").getThresholdValue())
                .isEqualTo(IdentityMatchThresholds.DETERMINISTIC_TAX_CODE);
    }

    @Test
    @DisplayName("Ngưỡng của các mốc điểm khớp đúng hằng số quyết định")
    void scoreThresholdsMatchConstants() {
        Map<String, IdentityMatchRuleResponse> byCode = rulesByCode();

        assertThat(byCode.get("SCORE_AUTO_MERGE").getThresholdValue())
                .isEqualTo(IdentityMatchThresholds.AUTO_MERGE_SCORE);
        assertThat(byCode.get("SCORE_PENDING_REVIEW").getThresholdValue())
                .isEqualTo(IdentityMatchThresholds.MATCH_CANDIDATE_SCORE);
        assertThat(byCode.get("SCORE_LOW_CONFIDENCE").getThresholdValue())
                .isEqualTo(IdentityMatchThresholds.LOW_CONFIDENCE_SCORE);
    }

    @Test
    @DisplayName("Thứ tự ưu tiên: khóa tiền định luôn đứng trước các mốc điểm")
    void deterministicRulesComeBeforeScoreBasedRules() {
        List<IdentityMatchRuleResponse> rules = service.getCatalog().getRules();

        int lastDeterministicBeforeScore = -1;
        int firstScoreBased = Integer.MAX_VALUE;
        for (int i = 0; i < rules.size(); i++) {
            if (Boolean.FALSE.equals(rules.get(i).getDeterministic())) {
                firstScoreBased = Math.min(firstScoreBased, i);
            }
        }
        for (int i = 0; i < firstScoreBased; i++) {
            assertThat(rules.get(i).getDeterministic()).isTrue();
            lastDeterministicBeforeScore = i;
        }

        assertThat(lastDeterministicBeforeScore).isGreaterThanOrEqualTo(0);
        assertThat(firstScoreBased).isLessThan(rules.size());
    }

    @Test
    @DisplayName("CCCD xếp trước MST — đúng thứ tự ưu tiên trong code")
    void identityNoRankedBeforeTaxCode() {
        Map<String, IdentityMatchRuleResponse> byCode = rulesByCode();

        assertThat(byCode.get("IDENTITY_NO").getOrder())
                .isLessThan(byCode.get("TAX_CODE_NAME_OK").getOrder());
    }

    @Test
    @DisplayName("Điểm CCCD > khóa nội bộ > MST, và cả ba không phải bội số của 5")
    void deterministicScoresKeepTheirOrderAndNonMultipleOfFiveProperty() {
        // Tính chất "không phải bội số của 5" là có chủ đích: mọi trọng số additive đều là bội số
        // của 5 nên điểm cộng dồn cũng vậy — nhờ đó nhìn score là biết candidate đến từ nhánh nào.
        int cccd = IdentityMatchThresholds.DETERMINISTIC_IDENTITY_NO;
        int typed = IdentityMatchThresholds.DETERMINISTIC_TYPED_ID;
        int tax = IdentityMatchThresholds.DETERMINISTIC_TAX_CODE;

        assertThat(cccd).isGreaterThan(typed);
        assertThat(typed).isGreaterThan(tax);
        assertThat(cccd % 5).isNotZero();
        assertThat(typed % 5).isNotZero();
        assertThat(tax % 5).isNotZero();
        assertThat(tax).isGreaterThanOrEqualTo(IdentityMatchThresholds.AUTO_MERGE_SCORE);
    }

    @Test
    @DisplayName("Mọi trọng số tín hiệu đều là bội số của 5")
    void allSignalWeightsAreMultiplesOfFive() {
        // Nếu test này đỏ thì tính chất ở test trên mất hiệu lực và phải chọn lại các mốc
        // deterministic (98/97/96) cho khỏi trùng với điểm cộng dồn.
        assertThat(service.getCatalog().getSignalWeights())
                .allSatisfy(w -> assertThat(w.getScore() % 5).isZero());
    }

    @Test
    @DisplayName("Bảng trọng số khớp đúng hằng số của scorer")
    void signalWeightsMatchScorerConstants() {
        Map<String, Integer> byCode = service.getCatalog().getSignalWeights().stream()
                .collect(Collectors.toMap(w -> w.getCode(), w -> w.getScore()));

        assertThat(byCode).containsEntry("IDENTITY_NO_MATCH", IdentityMatchThresholds.SCORE_IDENTITY_NO)
                .containsEntry("TAX_CODE_MATCH", IdentityMatchThresholds.SCORE_TAX_CODE)
                .containsEntry("PHONE_MATCH", IdentityMatchThresholds.SCORE_PHONE)
                .containsEntry("EMAIL_MATCH", IdentityMatchThresholds.SCORE_EMAIL)
                .containsEntry("NAME_EXACT_MATCH", IdentityMatchThresholds.SCORE_NAME_EXACT)
                .containsEntry("NAME_SIMILAR_90", IdentityMatchThresholds.SCORE_NAME_SIM_90)
                .containsEntry("NAME_SIMILAR_85", IdentityMatchThresholds.SCORE_NAME_SIM_85)
                .containsEntry("NAME_SIMILAR_75", IdentityMatchThresholds.SCORE_NAME_SIM_75)
                .containsEntry("DATE_OF_BIRTH_MATCH", IdentityMatchThresholds.SCORE_DOB)
                .containsEntry("PROVINCE_MATCH", IdentityMatchThresholds.SCORE_PROVINCE)
                .containsEntry("UNIT_MATCH", IdentityMatchThresholds.SCORE_UNIT);
    }

    @Test
    @DisplayName("Dải điểm của 3 mốc liền nhau, không hở và không chồng lấn")
    void scoreBandsAreContiguous() {
        assertThat(service.getCatalog().getRules())
                .filteredOn(r -> "SCORE_PENDING_REVIEW".equals(r.getCode()))
                .singleElement()
                .satisfies(r -> assertThat(r.getConfidenceThreshold()).isEqualTo(
                        IdentityMatchThresholds.MATCH_CANDIDATE_SCORE + "–"
                                + (IdentityMatchThresholds.AUTO_MERGE_SCORE - 1) + "%"));

        assertThat(service.getCatalog().getRules())
                .filteredOn(r -> "SCORE_LOW_CONFIDENCE".equals(r.getCode()))
                .singleElement()
                .satisfies(r -> assertThat(r.getConfidenceThreshold()).isEqualTo(
                        IdentityMatchThresholds.LOW_CONFIDENCE_SCORE + "–"
                                + (IdentityMatchThresholds.MATCH_CANDIDATE_SCORE - 1) + "%"));
    }

    @Test
    @DisplayName("Mọi rule đều ACTIVE và có đủ text hiển thị cho FE")
    void everyRuleIsActiveAndFullyPopulated() {
        assertThat(service.getCatalog().getRules()).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo(IdentityMatchRuleStatus.ACTIVE.name());
            assertThat(r.getStatusText()).isEqualTo(IdentityMatchRuleStatus.ACTIVE.getText());
            assertThat(r.getMatchKey()).isNotBlank();
            assertThat(r.getWeightLabel()).isNotBlank();
            assertThat(r.getConfidenceThreshold()).isNotBlank();
            assertThat(r.getDescription()).isNotBlank();
            assertThat(r.getPipeline()).isNotBlank();
            assertThat(IdentityMatchAction.fromValue(r.getAction())).isNotNull();
            assertThat(r.getActionText()).isEqualTo(IdentityMatchAction.fromValue(r.getAction()).getText());
        });
    }

    @Test
    @DisplayName("Có đủ cả 3 nhóm hành động")
    void allThreeActionsArePresent() {
        List<String> actions = service.getCatalog().getRules().stream()
                .map(IdentityMatchRuleResponse::getAction)
                .distinct()
                .toList();

        assertThat(actions).containsExactlyInAnyOrder(
                IdentityMatchAction.AUTO_MERGE.name(),
                IdentityMatchAction.PENDING_REVIEW.name(),
                IdentityMatchAction.LOW_CONFIDENCE.name());
    }

    @Test
    @DisplayName("Rule xung đột khóa mạnh phải là dòng cuối và gắn cờ chờ xác nhận")
    void conflictRuleIsLastAndNeedsReview() {
        List<IdentityMatchRuleResponse> rules = service.getCatalog().getRules();
        IdentityMatchRuleResponse last = rules.get(rules.size() - 1);

        assertThat(last.getCode()).isEqualTo("STRONG_KEY_CONFLICT");
        assertThat(last.getAction()).isEqualTo(IdentityMatchAction.PENDING_REVIEW.name());
    }

    @Test
    @DisplayName("maxScore khớp hằng số cap điểm")
    void maxScoreMatchesConstant() {
        IdentityMatchRuleCatalogResponse catalog = service.getCatalog();

        assertThat(catalog.getMaxScore()).isEqualTo(IdentityMatchThresholds.MAX_SCORE);
    }

    @Test
    @DisplayName("Rule khóa nội bộ liệt kê đúng các loại đang dùng để so khớp")
    void typedIdentifierRuleListsActualTypes() {
        String matchKey = rulesByCode().get("TYPED_IDENTIFIER").getMatchKey();

        assertThat(IdentityMatchThresholds.UNIQUE_TYPED_IDENTITY_TYPES)
                .allSatisfy(type -> assertThat(matchKey).contains(type));
        // DEVICE_ID/COOKIE_ID cố ý không tham gia auto-merge nên không được xuất hiện ở đây.
        assertThat(matchKey).doesNotContain("DEVICE_ID").doesNotContain("COOKIE_ID");
    }

    @Test
    @DisplayName("Có ghi chú nói rõ chưa hỗ trợ thêm/sửa rule")
    void notesMentionThatRuleEditingIsNotSupported() {
        assertThat(service.getCatalog().getNotes())
                .isNotEmpty()
                .anySatisfy(note -> assertThat(note).contains("chưa được hỗ trợ"));
    }

    @Test
    @DisplayName("Không rule nào hứa 'Tự động gộp' cho riêng luồng đối soát thủ công")
    void noRulePromisesAutoMergeOnAdminOnlyPipeline() {
        // Luồng admin KHÔNG bao giờ tự gộp: isAutoMergeRecommended() chỉ được hành động ở
        // ProfileMergeDecisionService (ingest), còn ProfileMatchCandidateServiceImpl chỉ ghi log.
        // Nếu một rule vừa AUTO_MERGE vừa pipeline=ADMIN thì bảng đang nói sai với người vận hành.
        assertThat(service.getCatalog().getRules())
                .noneSatisfy(r -> {
                    assertThat(r.getAction()).isEqualTo(IdentityMatchAction.AUTO_MERGE.name());
                    assertThat(r.getPipeline()).isEqualTo("ADMIN");
                });
    }

    @Test
    @DisplayName("Có ghi chú làm rõ cột Hành động chỉ nói về luồng tự động")
    void notesClarifyThatAdminFlowNeverAutoMerges() {
        assertThat(service.getCatalog().getNotes())
                .anySatisfy(note -> assertThat(note).contains("KHÔNG bao giờ tự gộp"));
    }

    @Test
    @DisplayName("Ngưỡng ≥95 chỉ hứa tự động gộp ở luồng nhận dữ liệu")
    void autoMergeScoreRuleIsIngestionOnly() {
        assertThat(rulesByCode().get("SCORE_AUTO_MERGE").getPipeline()).isEqualTo("INGESTION");
    }

    @Test
    @DisplayName("Cột Khóa khớp của các mốc điểm không lặp lại con số đã có ở cột Ngưỡng")
    void scoreBandMatchKeysDoNotDuplicateThreshold() {
        // Hai cột cạnh nhau cùng hiện "≥ 95" trên UI đọc như lỗi hiển thị.
        List<String> scoreBandCodes =
                List.of("SCORE_AUTO_MERGE", "SCORE_PENDING_REVIEW", "SCORE_LOW_CONFIDENCE");

        Map<String, IdentityMatchRuleResponse> byCode = rulesByCode();
        for (String code : scoreBandCodes) {
            IdentityMatchRuleResponse rule = byCode.get(code);
            assertThat(rule.getMatchKey())
                    .doesNotContain(String.valueOf(rule.getThresholdValue()));
        }
    }
}
