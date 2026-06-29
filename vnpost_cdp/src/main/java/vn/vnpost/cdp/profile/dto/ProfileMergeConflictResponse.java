package vn.vnpost.cdp.profile.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMergeConflictResponse {

    private Long id;
    private Long masterProfileId;
    private Long sourceRecordId;
    private String propertyName;
    private String currentValue;
    private String incomingValue;
    private String currentSource;
    private String incomingSource;
    private String conflictReason;
    private Short resolutionStatus;
    private String resolvedValue;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String createdBy;
    private LocalDateTime created;
    private LocalDateTime modified;
    private String modifiedBy;
}
