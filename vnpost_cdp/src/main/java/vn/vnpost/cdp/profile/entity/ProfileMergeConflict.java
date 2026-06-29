package vn.vnpost.cdp.profile.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.vnpost.cdp.common.entity.BaseEntity;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "profile_merge_conflicts")
public class ProfileMergeConflict extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "master_profile_id", nullable = false)
    private Long masterProfileId;

    @Column(name = "source_record_id")
    private Long sourceRecordId;

    @Column(name = "property_name", length = 200)
    private String propertyName;

    @Column(name = "current_value")
    private String currentValue;

    @Column(name = "incoming_value")
    private String incomingValue;

    @Column(name = "current_source", length = 100)
    private String currentSource;

    @Column(name = "incoming_source", length = 100)
    private String incomingSource;

    @Column(name = "conflict_reason")
    private String conflictReason;

    @Column(name = "resolution_status", nullable = false)
    private Short resolutionStatus = 0;

    @Column(name = "resolved_value")
    private String resolvedValue;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
