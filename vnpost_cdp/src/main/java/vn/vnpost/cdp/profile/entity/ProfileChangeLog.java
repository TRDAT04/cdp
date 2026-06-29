package vn.vnpost.cdp.profile.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "profile_change_logs")
public class ProfileChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "master_profile_id", nullable = false)
    private Long masterProfileId;

    @Column(name = "source_record_id")
    private Long sourceRecordId;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "property_name", length = 200)
    private String propertyName;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "selected_value")
    private String selectedValue;

    @Column(name = "old_source", length = 100)
    private String oldSource;

    @Column(name = "new_source", length = 100)
    private String newSource;

    @Column(name = "merge_strategy", length = 100)
    private String mergeStrategy;

    @Column(name = "reason")
    private String reason;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "changed_at")
    private LocalDateTime changedAt;
}
