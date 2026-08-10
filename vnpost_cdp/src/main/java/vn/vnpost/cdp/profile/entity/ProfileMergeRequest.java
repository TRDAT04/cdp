package vn.vnpost.cdp.profile.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import vn.vnpost.cdp.common.entity.BaseAuditFields;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Table("profile_merge_requests")
public class ProfileMergeRequest extends BaseAuditFields {

    @Id
    @Column("id")
    private Long id;

    @Column("source_master_profile_id")
    private Long sourceMasterProfileId;

    @Column("target_master_profile_id")
    private Long targetMasterProfileId;

    @Column("merge_reason")
    private String mergeReason;

    @Column("selected_values")
    private Map<String, Object> selectedValues;

    @Column("status")
    private Short status = 0;

    @Column("requested_by")
    private String requestedBy;

    @Column("approved_by")
    private String approvedBy;

    @Column("requested_at")
    private LocalDateTime requestedAt;

    @Column("approved_at")
    private LocalDateTime approvedAt;

    @Column("completed_at")
    private LocalDateTime completedAt;

    @Column("error_message")
    private String errorMessage;
}
