package vn.vnpost.cdp.profile.dto.match;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMatchScoreResult {

    private BigDecimal score;
    private String matchLevel;
    private Boolean autoMergeRecommended;
    private Boolean identityConflict;
    private List<ProfileMatchReasonCreateItem> reasons;
}
