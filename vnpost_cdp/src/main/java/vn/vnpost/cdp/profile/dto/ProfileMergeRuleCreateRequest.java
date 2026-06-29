package vn.vnpost.cdp.profile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileMergeRuleCreateRequest {

    @NotBlank
    private String propertyName;

    private String sourceSystem;

    private Integer priority;

    private String mergeStrategy;

    private Boolean allowOverwrite;

    private Boolean requireReview;

    private String description;
}
