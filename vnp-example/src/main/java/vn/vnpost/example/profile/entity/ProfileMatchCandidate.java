package vn.vnpost.example.profile.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import vn.vnpost.example.common.entity.BaseAuditFields;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Table("profile_match_candidates")
public class ProfileMatchCandidate extends BaseAuditFields {

    @Id
    @Column("id")
    private Long id;

    @Column("left_master_profile_id")
    private Long leftMasterProfileId;

    @Column("right_master_profile_id")
    private Long rightMasterProfileId;

    @Column("left_source_system")
    private String leftSourceSystem;

    @Column("left_source_customer_id")
    private String leftSourceCustomerId;

    @Column("right_source_system")
    private String rightSourceSystem;

    @Column("right_source_customer_id")
    private String rightSourceCustomerId;

    @Column("left_snapshot")
    private Map<String, Object> leftSnapshot;

    @Column("right_snapshot")
    private Map<String, Object> rightSnapshot;

    @Column("match_score")
    private BigDecimal matchScore;

    @Column("match_level")
    private String matchLevel;

    @Column("status")
    private Short status = 0;

    @Column("decision_by")
    private String decisionBy;

    @Column("decision_at")
    private LocalDateTime decisionAt;

    @Column("merge_request_id")
    private Long mergeRequestId;
}
