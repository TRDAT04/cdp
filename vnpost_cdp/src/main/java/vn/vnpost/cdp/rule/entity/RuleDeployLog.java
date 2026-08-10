package vn.vnpost.cdp.rule.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import vn.vnpost.cdp.common.entity.BaseAuditFields;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("rule_deploy_logs")
public class RuleDeployLog extends BaseAuditFields {

    @Id
    @Column("id")
    private Long id;

    @Column("rule_id")
    private String ruleId;

    @Column("rule_name")
    private String ruleName;

    @Column("scope")
    private String scope;

    @Column("event_type")
    private String eventType;

    @Column("payload_json")
    private Map<String, Object> payloadJson;

    @Column("status")
    private String status;

    @Column("unomi_response")
    private String unomiResponse;

    @Column("error_message")
    private String errorMessage;

    @Column("deployed_by")
    private String deployedBy;

    @Column("deployed_at")
    private Instant deployedAt;
}
