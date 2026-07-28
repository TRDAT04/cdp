package vn.vnpost.example.customer_event.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import vn.vnpost.example.common.entity.BaseAuditFields;

import java.time.LocalDateTime;
import java.util.Map;

@Table("customer_events")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEvent extends BaseAuditFields {

    @Id
    @Column("id")
    private Long id;

    @Column("event_code")
    private String eventCode;

    @Column("master_profile_id")
    private Long masterProfileId;

    @Column("profile_code")
    private String profileCode;

    @Column("event_type")
    private String eventType;

    @Column("session_id")
    private String sessionId;

    @Column("source_system")
    private String sourceSystem;

    @Column("source_customer_id")
    private String sourceCustomerId;

    @Column("occurred_at")
    private LocalDateTime occurredAt;

    @Column("properties")
    private Map<String, Object> properties;

    @Column("sync_status")
    private Short syncStatus = 0;

    @Column("synced_to_unomi_at")
    private LocalDateTime syncedToUnomiAt;

    @Column("source")
    private Map<String, Object> source;

    @Column("target")
    private Map<String, Object> target;
}
