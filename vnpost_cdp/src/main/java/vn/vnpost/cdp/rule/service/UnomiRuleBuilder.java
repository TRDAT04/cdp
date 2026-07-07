package vn.vnpost.cdp.rule.service;


import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.rule.config.RuleActionConfig;
import vn.vnpost.cdp.rule.config.RuleConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a valid Apache Unomi 2.4 rule JSON representation from a validated RuleConfig.
 */
@Component
public class UnomiRuleBuilder {



    public Map<String, Object> build(RuleConfig config) {
        Map<String, Object> rule = new LinkedHashMap<>();

        rule.put("metadata", buildMetadata(config));
        rule.put("condition", buildCondition(config));
        rule.put("actions", buildActions(config));
        
        rule.put("priority", config.getPriority());
        rule.put("raiseEventOnlyOnceForProfile", config.isRaiseEventOnlyOnceForProfile());
        rule.put("raiseEventOnlyOnceForSession", config.isRaiseEventOnlyOnceForSession());

        return rule;
    }

    private Map<String, Object> buildMetadata(RuleConfig config) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        
        String ruleId = StringUtils.hasText(config.getRuleId()) ? config.getRuleId() : generateRuleId(config);
        metadata.put("id", ruleId);
        metadata.put("name", config.getName());
        
        if (StringUtils.hasText(config.getDescription())) {
            metadata.put("description", config.getDescription());
        }
        
        metadata.put("scope", StringUtils.hasText(config.getScope()) ? config.getScope() : "systemscope");
        
        // Always enabled for newly deployed rules
        metadata.put("enabled", true);
        metadata.put("hidden", false);
        metadata.put("missingPlugins", false);
        metadata.put("readOnly", false);
        metadata.put("tags", List.of("rule-engine"));

        return metadata;
    }

    private Map<String, Object> buildCondition(RuleConfig config) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("type", "eventTypeCondition");
        
        Map<String, Object> parameterValues = new LinkedHashMap<>();
        parameterValues.put("eventTypeId", config.getEventType());
        condition.put("parameterValues", parameterValues);
        
        return condition;
    }

    private List<Map<String, Object>> buildActions(RuleConfig config) {
        List<Map<String, Object>> unomiActions = new ArrayList<>();
        
        for (RuleActionConfig actionConfig : config.getActions()) {
            Map<String, Object> action = new LinkedHashMap<>();
            Map<String, Object> parameterValues = new LinkedHashMap<>();
            
            switch (actionConfig.getType()) {
                case INCREMENT:
                    action.put("type", "incrementPropertyAction");
                    parameterValues.put("propertyName", actionConfig.getProfileProperty());
                    parameterValues.put("incrementBy", 1);
                    break;
                    
                case SUM:
                    action.put("type", "addToNumberAction");
                    parameterValues.put("eventProperty", actionConfig.getEventProperty());
                    parameterValues.put("profileProperty", actionConfig.getProfileProperty());
                    parameterValues.put("storeAsProperty", true);
                    break;

                case SET_PROPERTY:
                    action.put("type", "setPropertyAction");
                    parameterValues.put("setPropertyName","properties(" + actionConfig.getProfileProperty() + ")");
                    parameterValues.put("setPropertyValue","eventProperty::properties(" + actionConfig.getEventProperty() + ")");
                    parameterValues.put("setPropertyStrategy", "alwaysSet");
                    break;
                    
                case ADD_TO_SET:
                    action.put("type", "addToProfileSetsAction");
                    parameterValues.put("setPropertyName", actionConfig.getProfileProperty());
                    parameterValues.put("setPropertyValue",  actionConfig.getEventProperty());
                    break;
            }
            
            if (StringUtils.hasText(actionConfig.getDefaultValue())) {
                parameterValues.put("fallbackValue", actionConfig.getDefaultValue());
            }

            action.put("parameterValues", parameterValues);
            unomiActions.add(action);
        }
        
        return unomiActions;
    }

    private String generateRuleId(RuleConfig config) {
        String scope = StringUtils.hasText(config.getScope()) ? config.getScope() : "global";
        return scope + "-" + config.getEventType() + "-rule";
    }
}
