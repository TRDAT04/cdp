package vn.vnpost.cdp.rule.service.impl;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import vn.vnpost.cdp.config.UnomiProperties;
import vn.vnpost.cdp.rule.config.RuleConfig;
import vn.vnpost.cdp.rule.dto.DeployResult;
import vn.vnpost.cdp.rule.dto.RuleDetailResponse;
import vn.vnpost.cdp.rule.dto.RuleResponse;
import vn.vnpost.cdp.rule.dto.ValidationResult;
import vn.vnpost.cdp.rule.exception.RuleDeploymentException;
import vn.vnpost.cdp.rule.exception.RuleValidationException;
import vn.vnpost.cdp.rule.service.RuleEngineService;
import vn.vnpost.cdp.rule.service.RuleValidator;
import vn.vnpost.cdp.rule.service.UnomiRuleBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RuleEngineServiceImpl implements RuleEngineService {

    private final RuleValidator validator;
    private final UnomiRuleBuilder builder;
    private final WebClient unomiWebClient;
    private final UnomiProperties unomiProperties;

    public RuleEngineServiceImpl(RuleValidator validator, UnomiRuleBuilder builder, @Qualifier("unomiWebClient") WebClient unomiWebClient, UnomiProperties unomiProperties) {
        this.validator = validator;
        this.builder = builder;
        this.unomiWebClient = unomiWebClient;
        this.unomiProperties = unomiProperties;
    }

    @Override
    public ValidationResult validate(RuleConfig config) {
        log.debug("Validating RuleConfig for eventType: {}", config.getEventType());
        return validator.validate(config);
    }

    @Override
    public Map<String, Object> buildRule(RuleConfig config) {
        ValidationResult validationResult = validate(config);
        if (!validationResult.isValid()) {
            throw new RuleValidationException("RuleConfig validation failed", validationResult.violations());
        }

        // Inject default scope if not provided
        if (!StringUtils.hasText(config.getScope())) {
            config.setScope(unomiProperties.getScope());
        }

        return builder.build(config);
    }

    @Override
    public DeployResult deployRule(RuleConfig config) {
        Map<String, Object> unomiPayload = buildRule(config);

        Map<String, Object> metadata = (Map<String, Object>) unomiPayload.get("metadata");
        String ruleId = (String) metadata.get("id");

        log.info("Deploying rule '{}' to Apache Unomi...", ruleId);
        log.info("UNOMI RULE PAYLOAD >>> {}", unomiPayload);
        try {
            String unomiResponse = unomiWebClient.post()
                    .uri("/cxs/rules")
                    .bodyValue(unomiPayload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            log.info("Successfully deployed rule '{}'", ruleId);
            return new DeployResult(ruleId, "SUCCESS", unomiResponse, Instant.now());
            
        } catch (WebClientResponseException e) {
            log.error("Failed to deploy rule '{}'. Unomi returned HTTP {}: {}", ruleId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuleDeploymentException("Failed to deploy rule to Unomi. Status: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Failed to deploy rule '{}'. Error: {}", ruleId, e.getMessage(), e);
            throw new RuleDeploymentException("Failed to deploy rule to Unomi", e);
        }
    }
    @Override
    public List<RuleResponse> getAllRules() {

        List<Map<String, Object>> rules = unomiWebClient.get()
                .uri("/cxs/rules")
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (rules == null) {
            return List.of();
        }

        return rules.stream()
                .map(rule -> RuleResponse.builder()
                        .id((String) rule.get("id"))
                        .name((String) rule.get("name"))
                        .description((String) rule.get("description"))
                        .scope((String) rule.get("scope"))
                        .enabled((Boolean) rule.get("enabled"))
                        .readOnly((Boolean) rule.get("readOnly"))
                        .tags((List<String>) rule.getOrDefault("tags", List.of()))
                        .build()
                )
                .toList();
    }
    @Override
    public RuleDetailResponse getRuleDetail(String ruleId) {

        Map<String, Object> rule = unomiWebClient.get()
                .uri("/cxs/rules/{id}", ruleId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();


        if (rule == null) {
            return null;
        }


        Map<String, Object> metadata =
                (Map<String, Object>) rule.get("metadata");


        return RuleDetailResponse.builder()
                .id((String) rule.get("itemId"))
                .name(metadata != null ? (String) metadata.get("name") : null)
                .description(metadata != null ? (String) metadata.get("description") : null)
                .scope(metadata != null ? (String) metadata.get("scope") : null)
                .enabled(metadata != null && Boolean.TRUE.equals(metadata.get("enabled")))
                .priority((Integer) rule.get("priority"))
                .condition((Map<String, Object>) rule.get("condition"))
                .actions((List<Map<String,Object>>) rule.get("actions"))
                .build();
    }
}
