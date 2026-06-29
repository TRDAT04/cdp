package vn.vnpost.cdp.profile.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vnpost.cdp.common.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "profile_merge_requests")
public class ProfileMergeRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source_master_profile_id", nullable = false)
    private Long sourceMasterProfileId;

    @Column(name = "target_master_profile_id", nullable = false)
    private Long targetMasterProfileId;

    @Column(name = "merge_reason")
    private String mergeReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_values", columnDefinition = "jsonb")
    private Map<String, Object> selectedValues;

    @Column(name = "status", nullable = false)
    private Short status = 0;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message")
    private String errorMessage;
}
