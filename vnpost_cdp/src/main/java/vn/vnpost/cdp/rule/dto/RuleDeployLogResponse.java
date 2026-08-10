package vn.vnpost.cdp.rule.dto;

import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDeployLogResponse {
    private Long id;
    private String ruleId;
    private String ruleName;
    private String scope;
    private String eventType;
    private Map<String, Object> payloadJson;
    private String status;
    private String unomiResponse;
    private String errorMessage;
    private String deployedBy;
    private Instant deployedAt;

    // Audit fields
    private String createdBy;
    private LocalDateTime created;
    private LocalDateTime modified;
    private String modifiedBy;
}
