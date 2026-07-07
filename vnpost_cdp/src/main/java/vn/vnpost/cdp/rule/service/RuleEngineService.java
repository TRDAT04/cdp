package vn.vnpost.cdp.rule.service;



import vn.vnpost.cdp.rule.config.RuleConfig;
import vn.vnpost.cdp.rule.dto.DeployResult;
import vn.vnpost.cdp.rule.dto.ValidationResult;

import java.util.Map;

/**
 * Service for managing Unomi rules via configuration.
 */
public interface RuleEngineService {

    ValidationResult validate(RuleConfig config);

    Map<String, Object> buildRule(RuleConfig config);

    DeployResult deployRule(RuleConfig config);
}
