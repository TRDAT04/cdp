package vn.vnpost.cdp.profile.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMergeRequestResponse {

    private Long id;
    private Long sourceMasterProfileId;
    private Long targetMasterProfileId;
    private String mergeReason;
    private Map<String, Object> selectedValues;
    private Short status;
    private String requestedBy;
    private String approvedBy;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private String createdBy;
    private LocalDateTime created;
    private LocalDateTime modified;
    private String modifiedBy;
}
