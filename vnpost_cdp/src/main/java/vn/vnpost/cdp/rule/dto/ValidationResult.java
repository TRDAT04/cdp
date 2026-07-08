package vn.vnpost.cdp.rule.dto;

import java.util.List;


public record ValidationResult(boolean isValid, List<String> violations) {
}
