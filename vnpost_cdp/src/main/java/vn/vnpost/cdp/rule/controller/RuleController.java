package vn.vnpost.cdp.rule.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.rule.dto.RuleRequest;
import vn.vnpost.cdp.rule.service.RuleEngineService;
import vn.vnpost.shared.sercurity.CheckPermission;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleEngineService ruleEngineService;

    @GetMapping
    @CheckPermission(index = 1,title = "Xem danh sách rule")
    public Mono<ResponseEntity<MethodResult>> getAllRules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        Sort pageSort = StringUtils.hasText(sort)
                ? Sort.by(Sort.Direction.DESC, sort)
                : Sort.by(Sort.Direction.DESC, "deployedAt");
        Pageable pageable = PageRequest.of(page, size, pageSort);

        return ruleEngineService.getAllRuleLogs(pageable)
                .map(result -> ResponseEntity.ok(
                        MethodResult.success(result.getContent(), result.getTotalElements())));
    }

    @GetMapping("/{ruleId}")
    @CheckPermission(index = 2,title ="Xem chi tiết rule" )
    public Mono<ResponseEntity<MethodResult>> getRuleDetail(@PathVariable String ruleId) {
        return ruleEngineService.getRuleLogsByRuleId(ruleId)
                .map(logs -> ResponseEntity.ok(MethodResult.success(logs)));
    }

    @PostMapping("/validate")
    public Mono<ResponseEntity<MethodResult>> validate(@Valid @RequestBody RuleRequest request) {
        List<String> violations = ruleEngineService.validate(request);
        if (violations.isEmpty()) {
            return Mono.just(ResponseEntity.ok(MethodResult.success("Rule configuration is valid.")));
        }
        return Mono.just(ResponseEntity.badRequest()
                .body(MethodResult.error("Validation failed: " + String.join("; ", violations))));
    }


    @PostMapping("/build")
    public Mono<ResponseEntity<MethodResult>> buildRule(@Valid @RequestBody RuleRequest request) {
        return Mono.just(ResponseEntity.ok(MethodResult.success(ruleEngineService.buildRule(request))));
    }

    @PostMapping("/deploy")
    @CheckPermission(
            index = 5,
            title = "Deploy Rule"
    )
    public Mono<ResponseEntity<MethodResult>> deployRule(@Valid @RequestBody RuleRequest request) {
        return ruleEngineService.deployRule(request)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }
}
