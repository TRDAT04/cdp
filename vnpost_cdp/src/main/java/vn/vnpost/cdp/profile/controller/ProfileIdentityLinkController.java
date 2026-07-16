package vn.vnpost.cdp.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.dto.ProfileIdentityLinkCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileIdentityLinkUpdateRequest;
import vn.vnpost.cdp.profile.service.ProfileIdentityLinkService;

@RestController
@RequestMapping("/api/v1/admin/profile-identity-links")
public class ProfileIdentityLinkController {

    private final ProfileIdentityLinkService service;

    public ProfileIdentityLinkController(ProfileIdentityLinkService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MethodResult> create(@Valid @RequestBody ProfileIdentityLinkCreateRequest request) {
        return ResponseEntity.status(201).body(MethodResult.success(service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MethodResult> update(@PathVariable Long id,
                                               @RequestBody ProfileIdentityLinkUpdateRequest request) {
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

    @GetMapping("/by-identity")
    public ResponseEntity<MethodResult> listByIdentity(@RequestParam String identityType,
                                                       @RequestParam String identityValue) {
        return ResponseEntity.ok(MethodResult.success(service.listByIdentity(identityType, identityValue)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<MethodResult> changeStatus(@PathVariable Long id,
                                                     @RequestParam Short status) {
        return ResponseEntity.ok(MethodResult.success(service.changeStatus(id, status)));
    }
}
