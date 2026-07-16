package vn.vnpost.cdp.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.dto.ProfileMergeRuleCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeRuleTestRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeRuleUpdateRequest;
import vn.vnpost.cdp.profile.service.ProfileMergeRuleService;

@RestController
@RequestMapping("/api/v1/admin/profile-merge-rules")
public class ProfileMergeRuleController {

    private final ProfileMergeRuleService service;

    public ProfileMergeRuleController(ProfileMergeRuleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MethodResult> create(@Valid @RequestBody ProfileMergeRuleCreateRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MethodResult> update(@PathVariable Long id,
                                               @Valid @RequestBody ProfileMergeRuleUpdateRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MethodResult> getById(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.getById(id)));
    }

    @GetMapping
    public ResponseEntity<MethodResult> listActive() {
        return ResponseEntity.ok(MethodResult.success(service.listActive()));
    }

    @GetMapping("/property/{propertyName}")
    public ResponseEntity<MethodResult> listByPropertyName(@PathVariable String propertyName) {
        return ResponseEntity.ok(MethodResult.success(service.listByPropertyName(propertyName)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<MethodResult> changeStatus(@PathVariable Long id,
                                                     @RequestParam Short status) {
        return ResponseEntity.ok(MethodResult.success(service.changeStatus(id, status)));
    }

    @PostMapping("/test")
    public ResponseEntity<MethodResult> testRule(@Valid @RequestBody ProfileMergeRuleTestRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.testRule(request)));
    }
}
