package vn.vnpost.cdp.profile.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "profile_unomi_sync_logs")
public class ProfileUnomiSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "master_profile_id", nullable = false)
    private Long masterProfileId;

    @Column(name = "profile_code", nullable = false, length = 100)
    private String profileCode;

    @Column(name = "sync_type", length = 50)
    private String syncType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private Map<String, Object> requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private Map<String, Object> responsePayload;

    @Column(name = "status", nullable = false)
    private Short status = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
