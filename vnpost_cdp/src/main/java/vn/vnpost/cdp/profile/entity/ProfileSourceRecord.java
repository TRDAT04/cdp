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
@Table(name = "profile_source_records")
public class ProfileSourceRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "source_customer_id", length = 255)
    private String sourceCustomerId;

    @Column(name = "source_event_id", length = 255)
    private String sourceEventId;

    @Column(name = "master_profile_id")
    private Long masterProfileId;

    @Column(name = "identity_key", length = 500)
    private String identityKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_payload", columnDefinition = "jsonb")
    private Map<String, Object> normalizedPayload;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "merge_status")
    private Short mergeStatus = 0;

    @Column(name = "error_message")
    private String errorMessage;
}
