package vn.vnpost.cdp.rule.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.vnpost.cdp.rule.dto.DeployResult;
import vn.vnpost.cdp.rule.dto.RuleDeployLogResponse;
import vn.vnpost.cdp.rule.dto.RuleRequest;
import vn.vnpost.cdp.rule.entity.RuleDeployLog;

import java.util.List;
import java.util.Map;


public interface RuleEngineService {

    List<String> validate(RuleRequest request);

    Map<String, Object> buildRule(RuleRequest request);

    DeployResult deployRule(RuleRequest request);

    Page<RuleDeployLogResponse> getAllRuleLogs(Pageable pageable);

    List<RuleDeployLogResponse> getRuleLogsByRuleId(String ruleId);
}
