package vn.vnpost.cdp.rule.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.rule.dto.RuleRequest;
import vn.vnpost.cdp.rule.service.RuleEngineService;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleEngineService ruleEngineService;

    @GetMapping
    public ResponseEntity<MethodResult> getAllRules(
            @PageableDefault(sort = "deployedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(MethodResult.success(ruleEngineService.getAllRuleLogs(pageable)));
    }

    @GetMapping("/{ruleId}")
    public ResponseEntity<MethodResult> getRuleDetail(@PathVariable String ruleId) {
        return ResponseEntity.ok(MethodResult.success(ruleEngineService.getRuleLogsByRuleId(ruleId)));
    }

    @PostMapping("/validate")
    public ResponseEntity<MethodResult> validate(@Valid @RequestBody RuleRequest request) {
        List<String> violations = ruleEngineService.validate(request);
        if (violations.isEmpty()) {
            return ResponseEntity.ok(MethodResult.success("Rule configuration is valid."));
        }
        return ResponseEntity.badRequest()
                .body(MethodResult.error("Validation failed: " + String.join("; ", violations)));
    }


    @PostMapping("/build")
    public ResponseEntity<MethodResult> buildRule(@Valid @RequestBody RuleRequest request) {
        return ResponseEntity.ok(MethodResult.success(ruleEngineService.buildRule(request)));
    }

    @PostMapping("/deploy")
    public ResponseEntity<MethodResult> deployRule(@Valid @RequestBody RuleRequest request) {
        return ResponseEntity.ok(MethodResult.success(ruleEngineService.deployRule(request)));
    }
}
