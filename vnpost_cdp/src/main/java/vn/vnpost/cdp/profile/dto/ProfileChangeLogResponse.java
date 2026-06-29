package vn.vnpost.cdp.profile.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileChangeLogResponse {

    private Long id;

    private Long masterProfileId;

    private Long sourceRecordId;

    private String sourceSystem;

    private String eventType;

    private String propertyName;

    private String oldValue;

    private String newValue;

    private String selectedValue;

    private String oldSource;

    private String newSource;

    private String mergeStrategy;

    private String reason;

    private String changedBy;

    private LocalDateTime changedAt;
}
