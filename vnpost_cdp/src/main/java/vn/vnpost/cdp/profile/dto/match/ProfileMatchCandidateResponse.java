package vn.vnpost.cdp.profile.dto.match;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMatchCandidateResponse {

    private Long id;
    private BigDecimal matchScore;
    private String matchScorePercent;
    private String matchLevel;
    private String matchLevelText;
    private Short status;
    private String statusText;
    private String decisionBy;
    private LocalDateTime decisionAt;
    private Long mergeRequestId;
    private ProfileMatchSideResponse leftProfile;
    private ProfileMatchSideResponse rightProfile;
    private List<ProfileMatchReasonResponse> reasons;
    private LocalDateTime created;
}
