package vn.vnpost.cdp.rule.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vnpost.cdp.common.entity.BaseEntity;

import java.time.Instant;
import java.util.Map;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rule_deploy_logs")
public class RuleDeployLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", length = 255)
    private String ruleId;

    @Column(name = "rule_name", length = 255)
    private String ruleName;

    @Column(name = "scope", length = 100)
    private String scope;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private Map<String, Object> payloadJson;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "unomi_response", columnDefinition = "TEXT")
    private String unomiResponse;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "deployed_by", length = 100)
    private String deployedBy;

    @Column(name = "deployed_at")
    private Instant deployedAt;
}
