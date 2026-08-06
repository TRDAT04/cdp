package vn.vnpost.cdp.ingestion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.ingestion.dto.ProfileIngestionMessage;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chốt rằng luồng ingest ghi ra ĐÚNG dạng chuẩn mà pool candidate dùng để truy vấn.
 *
 * <p>Đây là bài test cho lỗi từng khiến việc khớp theo SĐT vô hiệu trên thực tế: service này từng
 * có bản chuẩn hoá riêng chỉ bỏ khoảng trắng/gạch nên giữ nguyên "+84912345678", trong khi pool và
 * scorer so sánh bằng IdentityUtils (ra "0912345678"). Giá trị được LƯU khác giá trị dùng để TRUY
 * VẤN → hai hồ sơ của cùng một người không bao giờ được ghép cặp để so. Test ở tầng IdentityUtils
 * KHÔNG bắt được lỗi đó, phải chốt ở đây.
 */
class ProfileNormalizationServiceTest {

    private final ProfileNormalizationService service = new ProfileNormalizationService();

    @Test
    @DisplayName("SĐT dạng +84 được ghi ra dạng nội địa 0…")
    void internationalPhoneIsStoredInDomesticForm() {
        NormalizedProfileData data = normalize(Map.of("phone", "+84912345678"));

        assertThat(data.getPhone()).isEqualTo("0912345678");
    }

    @Test
    @DisplayName("SĐT có khoảng trắng và dấu chấm cũng về dạng chuẩn")
    void formattedPhoneIsStoredInDomesticForm() {
        assertThat(normalize(Map.of("phone", "+84 912.345.678")).getPhone()).isEqualTo("0912345678");
        assertThat(normalize(Map.of("phone", "0912 345 678")).getPhone()).isEqualTo("0912345678");
    }

    @Test
    @DisplayName("Hai nguồn ghi SĐT khác dạng phải ra CÙNG một giá trị lưu trữ")
    void twoSourcesWritingDifferentFormsProduceSameStoredValue() {
        String fromCrm = normalize(Map.of("phone", "+84912345678")).getPhone();
        String fromPortal = normalize(Map.of("phone", "0912345678")).getPhone();

        assertThat(fromCrm).isEqualTo(fromPortal);
    }

    @Test
    @DisplayName("SĐT rác không còn chữ số nào → null, không phải chuỗi rỗng")
    void junkPhoneBecomesNull() {
        assertThat(normalize(Map.of("phone", "N/A")).getPhone()).isNull();
    }

    @Test
    @DisplayName("Email được ghi ở dạng chữ thường")
    void emailIsStoredLowercase() {
        assertThat(normalize(Map.of("email", " Nguyen.Van.A@VNPost.VN ")).getEmail())
                .isEqualTo("nguyen.van.a@vnpost.vn");
    }

    @Test
    @DisplayName("CCCD và MST bỏ khoảng trắng, in hoa")
    void identityNoAndTaxCodeAreCanonicalised() {
        NormalizedProfileData data = normalize(Map.of(
                "identityNo", "001 234 567 890",
                "taxCode", " 0101234567 "));

        assertThat(data.getIdentityNo()).isEqualTo("001234567890");
        assertThat(data.getTaxCode()).isEqualTo("0101234567");
    }

    @Test
    @DisplayName("normalizedPayload lưu lại cũng phải là giá trị đã chuẩn hoá")
    void normalizedPayloadCarriesCanonicalValues() {
        NormalizedProfileData data = normalize(Map.of("phone", "+84912345678"));

        assertThat(data.getNormalizedPayload()).containsEntry("phone", "0912345678");
    }

    @Test
    @DisplayName("Payload rỗng không làm nổ service")
    void emptyPayloadIsHandled() {
        NormalizedProfileData data = normalize(new HashMap<>());

        assertThat(data.getPhone()).isNull();
        assertThat(data.getEmail()).isNull();
        assertThat(data.getIdentityNo()).isNull();
    }

    private NormalizedProfileData normalize(Map<String, Object> payload) {
        return service.normalize(ProfileIngestionMessage.builder()
                .messageId("MSG-1")
                .sourceSystem("CRM")
                .sourceCustomerId("CRM-001")
                .eventType("PROFILE_CREATED")
                .payload(payload)
                .build());
    }
}
