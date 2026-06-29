package vn.vnpost.cdp.profile.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProfileIdentityLinkUpdateRequest {
    private String identityType;
    private String identityValue;
    private BigDecimal confidenceScore;
    private Boolean isPrimary;
    private Short status;
}
