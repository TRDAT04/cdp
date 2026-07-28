package vn.vnpost.example.profile.dto.match;

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
    private boolean autoMergeRecommended;
    private boolean identityConflict;
    private List<ProfileMatchReasonCreateItem> reasons;
}
