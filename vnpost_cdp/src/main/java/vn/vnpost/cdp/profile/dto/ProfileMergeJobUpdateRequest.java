package vn.vnpost.cdp.profile.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileMergeJobUpdateRequest {

    private Integer successRecords;

    private Integer conflictRecords;

    private Integer failedRecords;

    private String errorMessage;
}
