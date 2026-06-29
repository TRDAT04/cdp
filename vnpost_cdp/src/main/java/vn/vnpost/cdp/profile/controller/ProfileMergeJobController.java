package vn.vnpost.cdp.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.dto.ProfileMergeJobCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeJobUpdateRequest;
import vn.vnpost.cdp.profile.service.ProfileMergeJobService;

@RestController
@RequestMapping("/v1/admin/profile-merge-jobs")
public class ProfileMergeJobController {

    private final ProfileMergeJobService service;

    public ProfileMergeJobController(ProfileMergeJobService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MethodResult> create(@Valid @RequestBody ProfileMergeJobCreateRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MethodResult> getById(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.getById(id)));
    }

    @GetMapping("/by-type/{jobType}")
    public ResponseEntity<MethodResult> listByJobType(@PathVariable String jobType) {
        return ResponseEntity.ok(MethodResult.success(service.listByJobType(jobType)));
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<MethodResult> listByStatus(@PathVariable Short status) {
        return ResponseEntity.ok(MethodResult.success(service.listByStatus(status)));
    }

    @PostMapping("/{id}/running")
    public ResponseEntity<MethodResult> markRunning(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.markRunning(id)));
    }

    @PostMapping("/{id}/success")
    public ResponseEntity<MethodResult> markSuccess(@PathVariable Long id,
                                                    @RequestBody ProfileMergeJobUpdateRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.markSuccess(id, request)));
    }

    @PostMapping("/{id}/failed")
    public ResponseEntity<MethodResult> markFailed(@PathVariable Long id,
                                                   @RequestParam(required = false, defaultValue = "") String errorMessage) {
        return ResponseEntity.ok(MethodResult.success(service.markFailed(id, errorMessage)));
    }

    @PostMapping("/{id}/partial-success")
    public ResponseEntity<MethodResult> markPartialSuccess(@PathVariable Long id,
                                                           @RequestBody ProfileMergeJobUpdateRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.markPartialSuccess(id, request)));
    }
}
