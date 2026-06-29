package vn.vnpost.cdp.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.dto.ProfileMergeRequestApproveRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeRequestCreateRequest;
import vn.vnpost.cdp.profile.service.ProfileMergeRequestService;

@RestController
@RequestMapping("/v1/admin/profile-merge-requests")
public class ProfileMergeRequestController {

    private final ProfileMergeRequestService service;

    public ProfileMergeRequestController(ProfileMergeRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MethodResult> create(@Valid @RequestBody ProfileMergeRequestCreateRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MethodResult> getById(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.getById(id)));
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<MethodResult> listByStatus(@PathVariable Short status) {
        return ResponseEntity.ok(MethodResult.success(service.listByStatus(status)));
    }

    @GetMapping("/by-source/{sourceMasterProfileId}")
    public ResponseEntity<MethodResult> listBySourceProfile(@PathVariable Long sourceMasterProfileId) {
        return ResponseEntity.ok(MethodResult.success(service.listBySourceProfile(sourceMasterProfileId)));
    }

    @GetMapping("/by-target/{targetMasterProfileId}")
    public ResponseEntity<MethodResult> listByTargetProfile(@PathVariable Long targetMasterProfileId) {
        return ResponseEntity.ok(MethodResult.success(service.listByTargetProfile(targetMasterProfileId)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<MethodResult> approve(@PathVariable Long id,
                                                @RequestBody ProfileMergeRequestApproveRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.approve(id, request)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<MethodResult> reject(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.reject(id)));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<MethodResult> complete(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.complete(id)));
    }

    @PostMapping("/{id}/fail")
    public ResponseEntity<MethodResult> fail(@PathVariable Long id,
                                             @RequestParam(required = false, defaultValue = "") String errorMessage) {
        return ResponseEntity.ok(MethodResult.success(service.fail(id, errorMessage)));
    }
}
