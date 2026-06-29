package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMatchCandidateSummaryResponse {
    private Long id;
    private Long leftMasterProfileId;
    private Long rightMasterProfileId;
    private BigDecimal matchScore;
    private String matchScorePercent;
    private String matchLevel;
    private String matchLevelText;
    private Short status;
    private String statusText;
    private LocalDateTime created;
}
