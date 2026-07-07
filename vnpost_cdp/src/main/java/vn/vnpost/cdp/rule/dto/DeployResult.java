package vn.vnpost.cdp.rule.dto;

import java.time.Instant;

/**
 * Result of deploying a rule to Unomi.
 */
public record DeployResult(String ruleId, String status, String unomiResponse, Instant deployedAt) {
}
