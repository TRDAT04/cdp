package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Tab "Hành vi số". Dữ liệu hành vi lấy từ Apache Unomi.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDigitalBehaviorResponse {

    private List<String> segments;

    private Instant firstVisit;
    private Instant previousVisit;
    private Instant lastVisit;
    private Integer nbOfVisits;

    private Integer purchaseCount;
    private BigDecimal totalSpent;
    private Instant lastTransactionDate;
}
