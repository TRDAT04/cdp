package vn.vnpost.cdp.rule.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.rule.config.RuleConfig;
import vn.vnpost.cdp.rule.dto.DeployResult;
import vn.vnpost.cdp.rule.dto.RuleDetailResponse;
import vn.vnpost.cdp.rule.dto.RuleResponse;
import vn.vnpost.cdp.rule.dto.ValidationResult;
import vn.vnpost.cdp.rule.service.RuleEngineService;

import java.util.List;
import java.util.Map;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleEngineService ruleEngineService;

    @GetMapping
    public ResponseEntity<List<RuleResponse>> getAllRules() {
        return ResponseEntity.ok(ruleEngineService.getAllRules());
    }


    @GetMapping("/{ruleId}")
    public ResponseEntity<RuleDetailResponse> getRuleDetail(
            @PathVariable String ruleId) {

        return ResponseEntity.ok(
                ruleEngineService.getRuleDetail(ruleId)
        );
    }
    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validate(@Valid @RequestBody RuleConfig config) {
        ValidationResult result = ruleEngineService.validate(config);
        if (result.isValid()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    @PostMapping("/build")
    public ResponseEntity<Map<String, Object>> buildRule(@Valid @RequestBody RuleConfig config) {
        Map<String, Object> rulePayload = ruleEngineService.buildRule(config);
        return ResponseEntity.ok(rulePayload);
    }

    @PostMapping("/deploy")
    public ResponseEntity<DeployResult> deployRule(@Valid @RequestBody RuleConfig config) {
        DeployResult result = ruleEngineService.deployRule(config);
        return ResponseEntity.ok(result);
    }
}
