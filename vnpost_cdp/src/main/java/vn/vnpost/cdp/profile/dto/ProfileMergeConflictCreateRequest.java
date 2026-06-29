package vn.vnpost.cdp.profile.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileMergeConflictCreateRequest {

    @NotNull
    private Long masterProfileId;

    private Long sourceRecordId;

    private String propertyName;

    private String currentValue;

    private String incomingValue;

    private String currentSource;

    private String incomingSource;

    private String conflictReason;
}
