package vn.vnpost.example.rule.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import vn.vnpost.example.common.exception.BusinessException;
import vn.vnpost.example.config.UnomiProperties;
import vn.vnpost.example.rule.dto.*;
import vn.vnpost.example.rule.entity.RuleDeployLog;
import vn.vnpost.example.rule.repository.RuleDeployLogRepository;
import vn.vnpost.example.security.SecurityUtils;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineServiceImpl implements RuleEngineService {

    private static final Pattern VALID_PROPERTY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");

    @Qualifier("unomiWebClient")
    private final WebClient unomiWebClient;
    private final UnomiProperties unomiProperties;
    private final RuleDeployLogRepository deployLogRepository;

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    @Override
    public List<String> validate(RuleRequest request) {
        log.debug("Validating RuleRequest for eventType: {}", request.getEventType());
        return doValidate(request);
    }

    @Override
    public Map<String, Object> buildRule(RuleRequest request) {
        List<String> violations = doValidate(request);
        if (!violations.isEmpty()) {
            throw new BusinessException("RULE_VALIDATION_ERROR",
                    "Rule validation failed: " + String.join("; ", violations));
        }
        applyDefaultScope(request);
        return doBuild(request);
    }

    @Override
    public Mono<DeployResult> deployRule(RuleRequest request) {
        Map<String, Object> payload = buildRule(request);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
        String ruleId   = (String) metadata.get("id");
        String ruleName = (String) metadata.get("name");
        String scope    = (String) metadata.get("scope");

        return SecurityUtils.getCurrentUsernameOrSystem().flatMap(deployedBy -> {
            log.info("Deploying rule '{}' (scope={}) to Apache Unomi by '{}'", ruleId, scope, deployedBy);
            log.debug("Unomi rule payload >>> {}", payload);

            RuleDeployLog deployLog = RuleDeployLog.builder()
                    .ruleId(ruleId)
                    .ruleName(ruleName)
                    .scope(scope)
                    .eventType(request.getEventType())
                    .payloadJson(payload)
                    .deployedBy(deployedBy)
                    .deployedAt(Instant.now())
                    .build();

            return unomiWebClient.post()
                    .uri("/cxs/rules")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .flatMap(unomiResponse -> {
                        deployLog.setStatus("SUCCESS");
                        deployLog.setUnomiResponse(unomiResponse);
                        return deployLogRepository.save(deployLog)
                                .doOnNext(saved -> log.info("Successfully deployed rule '{}'", ruleId))
                                .thenReturn(new DeployResult(ruleId, "SUCCESS", unomiResponse,
                                        deployLog.getDeployedAt()));
                    })
                    .onErrorResume(WebClientResponseException.class, e -> {
                        String errorBody = e.getResponseBodyAsString();
                        log.error("Failed to deploy rule '{}'. Unomi returned HTTP {}: {}",
                                ruleId, e.getStatusCode(), errorBody);

                        deployLog.setStatus("FAILED");
                        deployLog.setErrorMessage("HTTP " + e.getStatusCode() + " — " + errorBody);
                        return deployLogRepository.save(deployLog)
                                .then(Mono.error(new BusinessException("RULE_DEPLOY_ERROR",
                                        "Failed to deploy rule to Unomi. Status: " + e.getStatusCode())));
                    })
                    .onErrorResume(ex -> !(ex instanceof BusinessException), ex -> {
                        log.error("Failed to deploy rule '{}'. Error: {}", ruleId, ex.getMessage(), ex);

                        deployLog.setStatus("FAILED");
                        deployLog.setErrorMessage(ex.getMessage());
                        return deployLogRepository.save(deployLog)
                                .then(Mono.error(new BusinessException("RULE_DEPLOY_ERROR",
                                        "Failed to deploy rule to Unomi: " + ex.getMessage())));
                    });
        });
    }

    @Override
    public Mono<Page<RuleDeployLogResponse>> getAllRuleLogs(Pageable pageable) {
        Mono<List<RuleDeployLogResponse>> contentMono = deployLogRepository
                .findAllByOrderByDeployedAtDesc(pageable)
                .map(this::mapToResponse)
                .collectList();
        return Mono.zip(contentMono, deployLogRepository.countAll())
                .map(t -> new PageImpl<>(t.getT1(), pageable, t.getT2()));
    }

    @Override
    public Mono<List<RuleDeployLogResponse>> getRuleLogsByRuleId(String ruleId) {
        return deployLogRepository.findByRuleIdOrderByDeployedAtDesc(ruleId)
                .map(this::mapToResponse)
                .collectList();
    }

    private RuleDeployLogResponse mapToResponse(RuleDeployLog log) {
        if (log == null) {
            return null;
        }
        return RuleDeployLogResponse.builder()
                .id(log.getId())
                .ruleId(log.getRuleId())
                .ruleName(log.getRuleName())
                .scope(log.getScope())
                .eventType(log.getEventType())
                .payloadJson(log.getPayloadJson())
                .status(log.getStatus())
                .unomiResponse(log.getUnomiResponse())
                .errorMessage(log.getErrorMessage())
                .deployedBy(log.getDeployedBy())
                .deployedAt(log.getDeployedAt())
                .createdBy(log.getCreatedBy())
                .created(log.getCreated())
                .modified(log.getModified())
                .modifiedBy(log.getModifiedBy())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // Validate (inline — từ RuleValidator )
    // ─────────────────────────────────────────────────────────────

    private List<String> doValidate(RuleRequest request) {
        List<String> violations = new ArrayList<>();

        if (!StringUtils.hasText(request.getEventType())) {
            violations.add("eventType must not be null or blank.");
        }

        if (request.getActions() == null || request.getActions().isEmpty()) {
            violations.add("actions list cannot be empty.");
        } else {
            Set<String> targetProperties = new HashSet<>();
            for (int i = 0; i < request.getActions().size(); i++) {
                validateAction(request.getActions().get(i), i, violations, targetProperties);
            }
        }
        return violations;
    }

    private void validateAction(RuleActionRequest action, int index,
                                List<String> violations, Set<String> targetProperties) {
        String prefix = "Action [" + index + "] ";

        if (action.getType() == null) {
            violations.add(prefix + "type must not be null.");
            return;
        }

        if (!StringUtils.hasText(action.getProfileProperty())) {
            violations.add(prefix + "profileProperty must not be null or blank.");
        } else {
            if (!VALID_PROPERTY_PATTERN.matcher(action.getProfileProperty()).matches()) {
                violations.add(prefix + "profileProperty '" + action.getProfileProperty()
                        + "' must match pattern: " + VALID_PROPERTY_PATTERN.pattern());
            }
            if (!targetProperties.add(action.getProfileProperty())) {
                violations.add(prefix + "duplicate profileProperty '"
                        + action.getProfileProperty() + "' detected across actions.");
            }
        }

        switch (action.getType()) {
            case SUM, SET_PROPERTY, ADD_TO_SET -> {
                if (!StringUtils.hasText(action.getEventProperty())) {
                    violations.add(prefix + "eventProperty must not be blank for action type "
                            + action.getType() + ".");
                }
            }
            case INCREMENT -> {
                // eventProperty không bắt buộc — tăng mặc định 1
            }
        }

        if (StringUtils.hasText(action.getDefaultValue())) {
            if (action.getType() == ActionType.SUM || action.getType() == ActionType.INCREMENT) {
                try {
                    Double.parseDouble(action.getDefaultValue());
                } catch (NumberFormatException e) {
                    violations.add(prefix + "defaultValue '" + action.getDefaultValue()
                            + "' must be numeric for action type " + action.getType() + ".");
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Build (inline — từ UnomiRuleBuilder cũ)
    // ─────────────────────────────────────────────────────────────

    private Map<String, Object> doBuild(RuleRequest request) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("metadata", buildMetadata(request));
        rule.put("condition", buildCondition(request));
        rule.put("actions", buildActions(request));
        rule.put("priority", request.getPriority());
        rule.put("raiseEventOnlyOnceForProfile", request.isRaiseEventOnlyOnceForProfile());
        rule.put("raiseEventOnlyOnceForSession", request.isRaiseEventOnlyOnceForSession());
        return rule;
    }

    private Map<String, Object> buildMetadata(RuleRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String ruleId = StringUtils.hasText(request.getRuleId())
                ? request.getRuleId()
                : generateRuleId(request);
        metadata.put("id", ruleId);
        metadata.put("name", request.getName());
        if (StringUtils.hasText(request.getDescription())) {
            metadata.put("description", request.getDescription());
        }
        metadata.put("scope", StringUtils.hasText(request.getScope())
                ? request.getScope() : "systemscope");
        metadata.put("enabled", true);
        metadata.put("hidden", false);
        metadata.put("missingPlugins", false);
        metadata.put("readOnly", false);
        metadata.put("tags", List.of("rule-engine", "vnpost-cdp"));
        return metadata;
    }

    private Map<String, Object> buildCondition(RuleRequest request) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("type", "eventTypeCondition");
        Map<String, Object> parameterValues = new LinkedHashMap<>();
        parameterValues.put("eventTypeId", request.getEventType());
        condition.put("parameterValues", parameterValues);
        return condition;
    }

    private List<Map<String, Object>> buildActions(RuleRequest request) {
        List<Map<String, Object>> unomiActions = new ArrayList<>();
        for (RuleActionRequest actionReq : request.getActions()) {
            Map<String, Object> action = new LinkedHashMap<>();
            Map<String, Object> params = new LinkedHashMap<>();

            switch (actionReq.getType()) {
                case INCREMENT -> {
                    action.put("type", "incrementPropertyAction");
                    params.put("propertyName", actionReq.getProfileProperty());
                    params.put("incrementBy", 1);
                }
                case SUM -> {
                    action.put("type", "addToNumberAction");
                    params.put("eventProperty", actionReq.getEventProperty());
                    params.put("profileProperty", actionReq.getProfileProperty());
                    params.put("storeAsProperty", true);
                }
                case SET_PROPERTY -> {
                    action.put("type", "setPropertyAction");
                    params.put("setPropertyName",
                            "properties(" + actionReq.getProfileProperty() + ")");
                    params.put("setPropertyValue",
                            "eventProperty::properties(" + actionReq.getEventProperty() + ")");
                    params.put("setPropertyStrategy", "alwaysSet");
                }
                case ADD_TO_SET -> {
                    action.put("type", "addToProfileSetsAction");
                    params.put("setPropertyName", actionReq.getProfileProperty());
                    params.put("setPropertyValue", actionReq.getEventProperty());
                }
            }

            if (StringUtils.hasText(actionReq.getDefaultValue())) {
                params.put("fallbackValue", actionReq.getDefaultValue());
            }

            action.put("parameterValues", params);
            unomiActions.add(action);
        }
        return unomiActions;
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private void applyDefaultScope(RuleRequest request) {
        if (!StringUtils.hasText(request.getScope())) {
            request.setScope(unomiProperties.getScope());
        }
    }

    private String generateRuleId(RuleRequest request) {
        String scope = StringUtils.hasText(request.getScope()) ? request.getScope() : "global";
        return scope + "-" + request.getEventType() + "-rule";
    }


}
