package vn.vnpost.cdp.profile.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileMergeRuleTestRequest {

    private String propertyName;

    private String currentValue;

    private String currentSource;

    private String incomingValue;

    private String incomingSource;
}
