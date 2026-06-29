package vn.vnpost.cdp.profile.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileMergeRuleUpdateRequest {

    private String sourceSystem;

    private Integer priority;

    private String mergeStrategy;

    private Boolean allowOverwrite;

    private Boolean requireReview;

    private String description;
}
