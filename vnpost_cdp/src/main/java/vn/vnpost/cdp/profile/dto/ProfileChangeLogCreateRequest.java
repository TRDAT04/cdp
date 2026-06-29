package vn.vnpost.cdp.profile.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileChangeLogCreateRequest {

    @NotNull
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
}
