package vn.vnpost.cdp.profile.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vnpost.cdp.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "profile_match_candidates")
public class ProfileMatchCandidate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "left_master_profile_id")
    private Long leftMasterProfileId;

    @Column(name = "right_master_profile_id")
    private Long rightMasterProfileId;

    @Column(name = "left_source_system", length = 100)
    private String leftSourceSystem;

    @Column(name = "left_source_customer_id", length = 255)
    private String leftSourceCustomerId;

    @Column(name = "right_source_system", length = 100)
    private String rightSourceSystem;

    @Column(name = "right_source_customer_id", length = 255)
    private String rightSourceCustomerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "left_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> leftSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "right_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> rightSnapshot;

    @Column(name = "match_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal matchScore;

    @Column(name = "match_level", length = 50)
    private String matchLevel;

    @Column(name = "status", nullable = false)
    private Short status = 0;

    @Column(name = "decision_by", length = 100)
    private String decisionBy;

    @Column(name = "decision_at")
    private LocalDateTime decisionAt;

    @Column(name = "merge_request_id")
    private Long mergeRequestId;
}
