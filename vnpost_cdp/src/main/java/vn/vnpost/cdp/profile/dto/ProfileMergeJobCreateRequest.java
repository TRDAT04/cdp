package vn.vnpost.cdp.profile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileMergeJobCreateRequest {

    @NotBlank
    private String jobType;

    private String sourceSystem;

    private Integer totalRecords;
}
