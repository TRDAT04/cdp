package vn.vnpost.cdp.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.dto.ProfileUnomiSyncLogCreateRequest;
import vn.vnpost.cdp.profile.service.ProfileUnomiSyncLogService;

import java.util.Map;

@RestController
@RequestMapping("/v1/admin/profile-unomi-sync-logs")
public class ProfileUnomiSyncLogController {

    private final ProfileUnomiSyncLogService service;

    public ProfileUnomiSyncLogController(ProfileUnomiSyncLogService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MethodResult> create(@Valid @RequestBody ProfileUnomiSyncLogCreateRequest request) {
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

    @GetMapping("/by-profile-code/{profileCode}")
    public ResponseEntity<MethodResult> listByProfileCode(@PathVariable String profileCode) {
        return ResponseEntity.ok(MethodResult.success(service.listByProfileCode(profileCode)));
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<MethodResult> listByStatus(@PathVariable Short status) {
        return ResponseEntity.ok(MethodResult.success(service.listByStatus(status)));
    }

    @PostMapping("/{id}/success")
    public ResponseEntity<MethodResult> markSuccess(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> responsePayload) {
        return ResponseEntity.ok(MethodResult.success(service.markSuccess(id, responsePayload)));
    }

    @PostMapping("/{id}/failed")
    public ResponseEntity<MethodResult> markFailed(@PathVariable Long id,
                                                   @RequestParam(required = false, defaultValue = "") String errorMessage) {
        return ResponseEntity.ok(MethodResult.success(service.markFailed(id, errorMessage)));
    }

    @PostMapping("/{id}/retrying")
    public ResponseEntity<MethodResult> markRetrying(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.markRetrying(id)));
    }
}
