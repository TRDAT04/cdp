package vn.vnpost.cdp.profile.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileAttributeValueResponse {

    private Long id;

    private Long masterProfileId;

    private Long sourceRecordId;

    private String sourceSystem;

    private String propertyName;

    private String propertyValue;

    private String normalizedValue;

    private BigDecimal confidenceScore;

    private Boolean isSelected;

    private LocalDateTime receivedAt;

    private String createdBy;

    private LocalDateTime created;

    private LocalDateTime modified;

    private String modifiedBy;
}
