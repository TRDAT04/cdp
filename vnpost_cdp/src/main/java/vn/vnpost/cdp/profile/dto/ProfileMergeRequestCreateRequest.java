package vn.vnpost.cdp.profile.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ProfileMergeRequestCreateRequest {

    @NotNull
    private Long sourceMasterProfileId;

    @NotNull
    private Long targetMasterProfileId;

    private String mergeReason;

    private Map<String, Object> selectedValues;
}
