package vn.vnpost.cdp.profile.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileSourceSystemUpdateRequest {
    private String name;
    private String description;
    private String sourceType;
    private Integer priority;
}
