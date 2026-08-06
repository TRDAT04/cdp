package vn.vnpost.cdp.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityUtilsTest {

    @ParameterizedTest
    @DisplayName("Mọi cách ghi SĐT Việt Nam đều về cùng một dạng chuẩn")
    @ValueSource(strings = {
            "0912345678",
            "+84912345678",
            "84912345678",
            "0084912345678",
            "+84 912 345 678",
            "0912-345-678",
            " 0912345678 ",
            "+84.912.345.678"
    })
    void normalizePhone_allVietnameseFormsCollapseToDomesticForm(String input) {
        assertThat(IdentityUtils.normalizePhone(input)).isEqualTo("0912345678");
    }

    @Test
    @DisplayName("Đây là lỗi từng làm rule 'chỉ SĐT trùng' vô hiệu: 2 dạng phải khớp nhau")
    void normalizePhone_internationalAndDomesticFormsMatchEachOther() {
        assertThat(IdentityUtils.normalizePhone("+84912345678"))
                .isEqualTo(IdentityUtils.normalizePhone("0912345678"));
    }

    @Test
    @DisplayName("SĐT không còn chữ số nào thì trả null, không trả chuỗi rỗng")
    void normalizePhone_returnsNullInsteadOfEmptyString() {
        // Chuỗi rỗng lọt vào DB sẽ khiến findByPhone("") khớp hàng loạt hồ sơ không liên quan.
        assertThat(IdentityUtils.normalizePhone("N/A")).isNull();
        assertThat(IdentityUtils.normalizePhone("---")).isNull();
        assertThat(IdentityUtils.normalizePhone("   ")).isNull();
        assertThat(IdentityUtils.normalizePhone(null)).isNull();
    }

    @Test
    @DisplayName("Số nội địa 9 chữ số bắt đầu bằng 84 không bị cắt thành 0")
    void normalizePhone_doesNotBreakShortDomesticNumberStartingWith84() {
        // length <= 9 nên không áp dụng luật mã quốc gia — giữ nguyên.
        assertThat(IdentityUtils.normalizePhone("849123456")).isEqualTo("849123456");
    }

    @ParameterizedTest
    @DisplayName("CCCD/MST bỏ khoảng trắng và in hoa")
    @CsvSource({
            "'001 234 567 890', 001234567890",
            "'0101234567',      0101234567",
            "'  0101234567  ',  0101234567",
            "'ma so 1a',        MASO1A"
    })
    void normalizeIdentityNo_stripsWhitespaceAndUppercases(String input, String expected) {
        assertThat(IdentityUtils.normalizeIdentityNo(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("MST chi nhánh và trụ sở KHÔNG được gộp thành một")
    void normalizeIdentityNo_keepsHyphenSoBranchIsNotMergedWithHeadquarters() {
        // "0101234567-001" là chi nhánh, "0101234567" là trụ sở — hai pháp nhân khác nhau.
        assertThat(IdentityUtils.normalizeIdentityNo("0101234567-001"))
                .isNotEqualTo(IdentityUtils.normalizeIdentityNo("0101234567"));
    }

    @Test
    @DisplayName("Email chuẩn hoá về chữ thường, bỏ khoảng trắng 2 đầu")
    void normalizeEmail_lowercasesAndTrims() {
        assertThat(IdentityUtils.normalizeEmail("  Nguyen.Van.A@VNPost.VN "))
                .isEqualTo("nguyen.van.a@vnpost.vn");
    }

    @Test
    @DisplayName("Tên bỏ dấu, về chữ thường, gộp khoảng trắng")
    void normalizeName_removesDiacriticsAndCollapsesSpaces() {
        assertThat(IdentityUtils.normalizeName("  Nguyễn   Văn  Á ")).isEqualTo("nguyen van a");
    }

    @Test
    @DisplayName("Tên khác dấu vẫn khớp 100% sau chuẩn hoá — cơ sở của rule 'tên gần đúng'")
    void calculateNameSimilarity_sameNameDifferentDiacriticsIsFullMatch() {
        String a = IdentityUtils.normalizeName("Nguyễn Văn A");
        String b = IdentityUtils.normalizeName("NGUYEN VAN A");
        assertThat(a).isEqualTo(b);
        assertThat(IdentityUtils.calculateNameSimilarity(a, b)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Hai tên khác nhau rõ rệt phải dưới ngưỡng 75%")
    void calculateNameSimilarity_differentNamesFallBelowThreshold() {
        double sim = IdentityUtils.calculateNameSimilarity(
                IdentityUtils.normalizeName("Nguyễn Văn A"),
                IdentityUtils.normalizeName("Trần Thị Bích Ngọc"));
        assertThat(sim).isLessThan(75.0);
    }
}
