package vn.vnpost.cdp.rule.dto;

import java.time.Instant;


public record DeployResult(String ruleId, String status, String unomiResponse, Instant deployedAt) {
}
