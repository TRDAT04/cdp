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
public class ProfileIdentityLinkDetailResponse {
    private Long id;
    private String sourceSystem;
    private String sourceCustomerId;
    private String identityType;
    private String identityValue;
    private BigDecimal confidenceScore;
    private Boolean isPrimary;
    private Short status;
    private String statusText;
    private LocalDateTime linkedAt;
    private String linkedBy;
}
