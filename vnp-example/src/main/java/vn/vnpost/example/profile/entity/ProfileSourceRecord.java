package vn.vnpost.example.profile.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import vn.vnpost.example.common.entity.BaseAuditFields;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Table("profile_source_records")
public class ProfileSourceRecord extends BaseAuditFields {

    @Id
    @Column("id")
    private Long id;

    @Column("source_system")
    private String sourceSystem;

    @Column("source_customer_id")
    private String sourceCustomerId;

    @Column("source_event_id")
    private String sourceEventId;

    @Column("master_profile_id")
    private Long masterProfileId;

    @Column("identity_key")
    private String identityKey;

    @Column("raw_payload")
    private Map<String, Object> rawPayload;

    @Column("normalized_payload")
    private Map<String, Object> normalizedPayload;

    @Column("received_at")
    private LocalDateTime receivedAt;

    @Column("processed_at")
    private LocalDateTime processedAt;

    @Column("merge_status")
    private Short mergeStatus = 0;

    @Column("error_message")
    private String errorMessage;
}
