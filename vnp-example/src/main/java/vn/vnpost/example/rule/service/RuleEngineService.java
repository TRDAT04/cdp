package vn.vnpost.example.rule.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;
import vn.vnpost.example.rule.dto.DeployResult;
import vn.vnpost.example.rule.dto.RuleDeployLogResponse;
import vn.vnpost.example.rule.dto.RuleRequest;

import java.util.List;
import java.util.Map;


public interface RuleEngineService {

    /** Thuần in-memory, không I/O — giữ đồng bộ. */
    List<String> validate(RuleRequest request);

    /** Thuần in-memory, không I/O — giữ đồng bộ. */
    Map<String, Object> buildRule(RuleRequest request);

    Mono<DeployResult> deployRule(RuleRequest request);

    Mono<Page<RuleDeployLogResponse>> getAllRuleLogs(Pageable pageable);

    Mono<List<RuleDeployLogResponse>> getRuleLogsByRuleId(String ruleId);
}
