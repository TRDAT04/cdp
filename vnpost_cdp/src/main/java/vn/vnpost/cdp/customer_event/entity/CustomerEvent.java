package vn.vnpost.cdp.customer_event.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vnpost.cdp.common.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "customer_events")

public class CustomerEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_code", nullable = false, unique = true, length = 100)
    private String eventCode;

    @Column(name = "master_profile_id")
    private Long masterProfileId;

    @Column(name = "profile_code", length = 100)
    private String profileCode;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Column(name = "source_system", nullable = false, length = 100)
    private String sourceSystem;

    @Column(name = "source_customer_id", nullable = false, length = 255)
    private String sourceCustomerId;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private Map<String, Object> properties;

    @Column(name = "sync_status")
    private Short syncStatus = 0;

    @Column(name = "synced_to_unomi_at")
    private LocalDateTime syncedToUnomiAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source", columnDefinition = "jsonb")
    private Map<String, Object> source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target", columnDefinition = "jsonb")
    private Map<String, Object> target;
}