package vn.vnpost.cdp.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.dto.ProfileChangeLogCreateRequest;
import vn.vnpost.cdp.profile.service.ProfileChangeLogService;

@RestController
@RequestMapping("/v1/admin/profile-change-logs")
public class ProfileChangeLogController {

    private final ProfileChangeLogService service;

    public ProfileChangeLogController(ProfileChangeLogService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MethodResult> create(@Valid @RequestBody ProfileChangeLogCreateRequest request) {
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

    @GetMapping("/by-profile/{masterProfileId}/property/{propertyName}")
    public ResponseEntity<MethodResult> listByPropertyName(@PathVariable Long masterProfileId,
                                                           @PathVariable String propertyName) {
        return ResponseEntity.ok(MethodResult.success(service.listByPropertyName(masterProfileId, propertyName)));
    }
}
