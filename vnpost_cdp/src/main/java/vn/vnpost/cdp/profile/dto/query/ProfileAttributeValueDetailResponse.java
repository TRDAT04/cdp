package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileAttributeValueDetailResponse {
    private Long id;
    private String sourceSystem;
    private String propertyName;
    private String propertyValue;
    private String normalizedValue;
    private BigDecimal confidenceScore;
    private Boolean isSelected;
    private LocalDateTime receivedAt;
}
