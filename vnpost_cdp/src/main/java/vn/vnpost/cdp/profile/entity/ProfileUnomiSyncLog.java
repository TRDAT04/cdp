package vn.vnpost.cdp.profile.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Table("profile_unomi_sync_logs")
public class ProfileUnomiSyncLog {

    @Id
    @Column("id")
    private Long id;

    @Column("master_profile_id")
    private Long masterProfileId;

    @Column("profile_code")
    private String profileCode;

    @Column("sync_type")
    private String syncType;

    @Column("request_payload")
    private Map<String, Object> requestPayload;

    @Column("response_payload")
    private Map<String, Object> responsePayload;

    @Column("status")
    private Short status = 0;

    @Column("error_message")
    private String errorMessage;

    @Column("synced_at")
    private LocalDateTime syncedAt;

    @Column("created_by")
    private String createdBy;
}
