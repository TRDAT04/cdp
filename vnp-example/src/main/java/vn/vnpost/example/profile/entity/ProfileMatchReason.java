package vn.vnpost.example.profile.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Getter
@Setter
@Table("profile_match_reasons")
public class ProfileMatchReason {

    @Id
    @Column("id")
    private Long id;

    @Column("match_candidate_id")
    private Long matchCandidateId;

    @Column("reason_type")
    private String reasonType;

    @Column("reason_message")
    private String reasonMessage;

    @Column("left_value")
    private String leftValue;

    @Column("right_value")
    private String rightValue;

    @Column("score")
    private BigDecimal score;
}
