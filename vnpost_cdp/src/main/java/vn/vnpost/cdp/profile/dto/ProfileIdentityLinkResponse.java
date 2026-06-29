package vn.vnpost.cdp.profile.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileIdentityLinkResponse {
    private Long id;
    private Long masterProfileId;
    private String sourceSystem;
    private String sourceCustomerId;
    private String identityType;
    private String identityValue;
    private BigDecimal confidenceScore;
    private Boolean isPrimary;
    private Short status;
    private LocalDateTime linkedAt;
    private String linkedBy;
    private String createdBy;
    private LocalDateTime created;
    private LocalDateTime modified;
    private String modifiedBy;
}
