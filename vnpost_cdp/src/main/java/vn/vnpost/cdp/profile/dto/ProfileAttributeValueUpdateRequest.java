package vn.vnpost.cdp.profile.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProfileAttributeValueUpdateRequest {

    private String propertyValue;

    private String normalizedValue;

    private BigDecimal confidenceScore;

    private Boolean isSelected;
}
