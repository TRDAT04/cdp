package vn.vnpost.cdp.profile.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProfileAttributeValueCreateRequest {

    @NotNull
    private Long masterProfileId;

    private Long sourceRecordId;

    private String sourceSystem;

    private String propertyName;

    private String propertyValue;

    private String normalizedValue;

    private BigDecimal confidenceScore;

    private Boolean isSelected;

    private LocalDateTime receivedAt;
}
