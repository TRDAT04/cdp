package vn.vnpost.example.profile.dto.match;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ProfileMatchCandidateSearchRequest {

    private Short status;
    private String matchLevel;
    private BigDecimal minScore;
    private String sourceSystem;
    private String keyword;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
}
