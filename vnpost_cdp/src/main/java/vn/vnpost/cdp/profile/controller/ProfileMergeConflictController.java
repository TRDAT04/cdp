package vn.vnpost.cdp.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.dto.ProfileMergeConflictCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeConflictResolveRequest;
import vn.vnpost.cdp.profile.service.ProfileMergeConflictService;

@RestController
@RequestMapping("/api/v1/admin/profile-merge-conflicts")
public class ProfileMergeConflictController {

    private final ProfileMergeConflictService service;

    public ProfileMergeConflictController(ProfileMergeConflictService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MethodResult> create(@Valid @RequestBody ProfileMergeConflictCreateRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MethodResult> getById(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.getById(id)));
    }

    @GetMapping("/by-profile/{masterProfileId}")
    public ResponseEntity<MethodResult> listByMasterProfileId(@PathVariable Long masterProfileId) {
        return ResponseEntity.ok(MethodResult.success(service.listByMasterProfileId(masterProfileId)));
    }

    @GetMapping("/by-status/{resolutionStatus}")
    public ResponseEntity<MethodResult> listByResolutionStatus(@PathVariable Short resolutionStatus) {
        return ResponseEntity.ok(MethodResult.success(service.listByResolutionStatus(resolutionStatus)));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<MethodResult> resolve(@PathVariable Long id,
                                                @RequestBody ProfileMergeConflictResolveRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.resolve(id, request)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<MethodResult> reject(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.reject(id)));
    }

    @PostMapping("/{id}/ignore")
    public ResponseEntity<MethodResult> ignore(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.ignore(id)));
    }
}
