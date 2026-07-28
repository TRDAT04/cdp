package vn.vnpost.example.profile.dto.match;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProfileMatchCandidateCreateRequest {

    @NotNull(message = "leftMasterProfileId must not be null")
    private Long leftMasterProfileId;

    @NotNull(message = "rightMasterProfileId must not be null")
    private Long rightMasterProfileId;
}
