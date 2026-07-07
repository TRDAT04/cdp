package vn.vnpost.cdp.rule.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.vnpost.cdp.rule.config.RuleActionConfig;
import vn.vnpost.cdp.rule.config.RuleConfig;
import vn.vnpost.cdp.rule.dto.ActionType;
import vn.vnpost.cdp.rule.dto.ValidationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates RuleConfig to ensure it produces safe, valid Apache Unomi rules.
 */
@Component
public class RuleValidator {

    private static final Pattern VALID_PROPERTY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");

    public ValidationResult validate(RuleConfig config) {
        List<String> violations = new ArrayList<>();

        if (!StringUtils.hasText(config.getEventType())) {
            violations.add("eventType must not be null or blank.");
        }

        if (config.getActions() == null || config.getActions().isEmpty()) {
            violations.add("actions list cannot be empty.");
        } else {
            Set<String> targetProperties = new HashSet<>();
            for (int i = 0; i < config.getActions().size(); i++) {
                RuleActionConfig action = config.getActions().get(i);
                validateAction(action, i, violations, targetProperties);
            }
        }

        return new ValidationResult(violations.isEmpty(), violations);
    }

    private void validateAction(RuleActionConfig action, int index, List<String> violations, Set<String> targetProperties) {
        String prefix = "Action [" + index + "] ";

        if (action.getType() == null) {
            violations.add(prefix + "type must not be null.");
            return;
        }

        if (!StringUtils.hasText(action.getProfileProperty())) {
            violations.add(prefix + "profileProperty must not be null or blank.");
        } else {
            if (!VALID_PROPERTY_PATTERN.matcher(action.getProfileProperty()).matches()) {
                violations.add(prefix + "profileProperty '" + action.getProfileProperty() + "' must match pattern: " + VALID_PROPERTY_PATTERN.pattern());
            }
            if (!targetProperties.add(action.getProfileProperty())) {
                violations.add(prefix + "duplicate profileProperty '" + action.getProfileProperty() + "' detected across actions.");
            }
        }

        switch (action.getType()) {
            case SUM:
            case SET_PROPERTY:
            case ADD_TO_SET:
                if (!StringUtils.hasText(action.getEventProperty())) {
                    violations.add(prefix + "eventProperty must not be blank for action type " + action.getType() + ".");
                }
                break;
            case INCREMENT:
                // eventProperty is not strictly required for INCREMENT since it defaults to incrementing by 1
                break;
        }

        if (StringUtils.hasText(action.getDefaultValue())) {
            if (action.getType() == ActionType.SUM || action.getType() == ActionType.INCREMENT) {
                try {
                    Double.parseDouble(action.getDefaultValue());
                } catch (NumberFormatException e) {
                    violations.add(prefix + "defaultValue '" + action.getDefaultValue() + "' must be numeric for action type " + action.getType() + ".");
                }
            }
        }
    }
}
