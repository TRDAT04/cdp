package vn.vnpost.cdp.profile.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMergeRuleResponse {

    private Long id;

    private String propertyName;

    private String sourceSystem;

    private Integer priority;

    private String mergeStrategy;

    private Boolean allowOverwrite;

    private Boolean requireReview;

    private String description;

    private Short status;

    private String createdBy;

    private LocalDateTime created;

    private LocalDateTime modified;

    private String modifiedBy;
}
