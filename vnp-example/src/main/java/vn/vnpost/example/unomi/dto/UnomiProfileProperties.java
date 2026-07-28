package vn.vnpost.example.unomi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Typed DTO đại diện cho trường {@code properties} trong response profile của Apache Unomi.
 * <p>
 * Các trường thời gian được giữ kiểu {@link Instant} (UTC) để không mất thông tin múi giờ.
 * Chỉ convert sang kiểu khác khi API response thực sự cần thiết.
 * </p>
 * <p>
 * Annotated với {@code @JsonIgnoreProperties(ignoreUnknown = true)} để an toàn khi
 * Unomi thêm các trường mới mà chưa cần mapping.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnomiProfileProperties {

    /** Mã profile CDP, tương ứng với {@code MasterProfile.profileCode}. */
    @JsonProperty("cdpProfileCode")
    private String cdpProfileCode;

    /** Thời điểm truy cập đầu tiên được Unomi ghi nhận. */
    @JsonProperty("firstVisit")
    private Instant firstVisit;

    /** Thời điểm truy cập trước lần cuối cùng. */
    @JsonProperty("previousVisit")
    private Instant previousVisit;

    /** Thời điểm truy cập gần nhất. */
    @JsonProperty("lastVisit")
    private Instant lastVisit;

    /** Tổng số lần truy cập. */
    @JsonProperty("nbOfVisits")
    private Integer nbOfVisits;

    /** Tổng số lần mua hàng. */
    @JsonProperty("purchaseCount")
    private Integer purchaseCount;

    /** Tổng giá trị giao dịch (đơn vị: đồng). */
    @JsonProperty("totalSpent")
    private BigDecimal totalSpent;

    /** Thời điểm giao dịch gần nhất. */
    @JsonProperty("lastTransactionDate")
    private Instant lastTransactionDate;
}
