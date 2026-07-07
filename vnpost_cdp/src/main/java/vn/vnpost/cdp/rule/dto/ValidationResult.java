package vn.vnpost.cdp.rule.dto;

import java.util.List;

/**
 * Result of validating a RuleConfig.
 */
public record ValidationResult(boolean isValid, List<String> violations) {
}
