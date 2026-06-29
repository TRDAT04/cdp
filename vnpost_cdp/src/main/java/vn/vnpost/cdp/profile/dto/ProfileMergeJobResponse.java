package vn.vnpost.cdp.profile.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMergeJobResponse {

    private Long id;
    private String jobType;
    private String sourceSystem;
    private Integer totalRecords;
    private Integer successRecords;
    private Integer conflictRecords;
    private Integer failedRecords;
    private Short status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private String createdBy;
    private LocalDateTime created;
    private LocalDateTime modified;
    private String modifiedBy;
}
