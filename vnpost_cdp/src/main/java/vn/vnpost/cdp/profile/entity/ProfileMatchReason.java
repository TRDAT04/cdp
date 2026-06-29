package vn.vnpost.cdp.profile.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "profile_match_reasons")
public class ProfileMatchReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "match_candidate_id", nullable = false)
    private Long matchCandidateId;

    @Column(name = "reason_type", nullable = false, length = 100)
    private String reasonType;

    @Column(name = "reason_message", nullable = false, length = 500)
    private String reasonMessage;

    @Column(name = "left_value")
    private String leftValue;

    @Column(name = "right_value")
    private String rightValue;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

}
