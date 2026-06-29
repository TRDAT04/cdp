package vn.vnpost.cdp.profile.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMergeRuleTestResponse {

    private String selectedValue;

    private String selectedSource;

    private String reason;
}
