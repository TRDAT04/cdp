package vn.vnpost.example.profile.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import vn.vnpost.example.common.entity.BaseAuditFields;

import java.time.LocalDateTime;

@Getter
@Setter
@Table("profile_merge_conflicts")
public class ProfileMergeConflict extends BaseAuditFields {

    @Id
    @Column("id")
    private Long id;

    @Column("master_profile_id")
    private Long masterProfileId;

    @Column("source_record_id")
    private Long sourceRecordId;

    @Column("property_name")
    private String propertyName;

    @Column("current_value")
    private String currentValue;

    @Column("incoming_value")
    private String incomingValue;

    @Column("current_source")
    private String currentSource;

    @Column("incoming_source")
    private String incomingSource;

    @Column("conflict_reason")
    private String conflictReason;

    @Column("resolution_status")
    private Short resolutionStatus = 0;

    @Column("resolved_value")
    private String resolvedValue;

    @Column("resolved_by")
    private String resolvedBy;

    @Column("resolved_at")
    private LocalDateTime resolvedAt;
}
