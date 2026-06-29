package vn.vnpost.cdp.profile.dto.match;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProfileCandidateMergeRequest {

    private Long targetMasterProfileId;
    private String mergeReason;
}
