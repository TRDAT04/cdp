package vn.vnpost.cdp.profile.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProfileIdentityLinkCreateRequest {

    @NotNull(message = "masterProfileId must not be null")
    private Long masterProfileId;

    private String sourceSystem;
    private String sourceCustomerId;
    private String identityType;
    private String identityValue;
    private BigDecimal confidenceScore;
    private Boolean isPrimary;
}
