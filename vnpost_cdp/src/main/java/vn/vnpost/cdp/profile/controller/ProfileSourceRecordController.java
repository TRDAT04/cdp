package vn.vnpost.cdp.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.dto.ProfileSourceRecordCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileSourceRecordUpdateRequest;
import vn.vnpost.cdp.profile.service.ProfileSourceRecordService;

@RestController
@RequestMapping("/api/v1/admin/profile-source-records")
public class ProfileSourceRecordController {

    private final ProfileSourceRecordService service;

    public ProfileSourceRecordController(ProfileSourceRecordService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MethodResult> create(@Valid @RequestBody ProfileSourceRecordCreateRequest request) {
        return ResponseEntity.status(201).body(MethodResult.success(service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MethodResult> update(@PathVariable Long id,
                                               @RequestBody ProfileSourceRecordUpdateRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MethodResult> getById(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.getById(id)));
    }

    @GetMapping("/by-profile/{masterProfileId}")
    public ResponseEntity<MethodResult> listByMasterProfileId(@PathVariable Long masterProfileId) {
        return ResponseEntity.ok(MethodResult.success(service.listByMasterProfileId(masterProfileId)));
    }

    @GetMapping("/by-source/{sourceSystem}")
    public ResponseEntity<MethodResult> listBySourceSystem(@PathVariable String sourceSystem) {
        return ResponseEntity.ok(MethodResult.success(service.listBySourceSystem(sourceSystem)));
    }

    @GetMapping("/by-status/{mergeStatus}")
    public ResponseEntity<MethodResult> listByMergeStatus(@PathVariable Short mergeStatus) {
        return ResponseEntity.ok(MethodResult.success(service.listByMergeStatus(mergeStatus)));
    }

    @PatchMapping("/{id}/mark-processed")
    public ResponseEntity<MethodResult> markProcessed(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.markProcessed(id)));
    }

    @PatchMapping("/{id}/mark-error")
    public ResponseEntity<MethodResult> markError(@PathVariable Long id,
                                                  @RequestParam(required = false, defaultValue = "") String errorMessage) {
        return ResponseEntity.ok(MethodResult.success(service.markError(id, errorMessage)));
    }
}
