package vn.vnpost.example.profile.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Setter
@Table("profile_change_logs")
public class ProfileChangeLog {

    @Id
    @Column("id")
    private Long id;

    @Column("master_profile_id")
    private Long masterProfileId;

    @Column("source_record_id")
    private Long sourceRecordId;

    @Column("source_system")
    private String sourceSystem;

    @Column("event_type")
    private String eventType;

    @Column("property_name")
    private String propertyName;

    @Column("old_value")
    private String oldValue;

    @Column("new_value")
    private String newValue;

    @Column("selected_value")
    private String selectedValue;

    @Column("old_source")
    private String oldSource;

    @Column("new_source")
    private String newSource;

    @Column("merge_strategy")
    private String mergeStrategy;

    @Column("reason")
    private String reason;

    @Column("changed_by")
    private String changedBy;

    @Column("changed_at")
    private LocalDateTime changedAt;
}
