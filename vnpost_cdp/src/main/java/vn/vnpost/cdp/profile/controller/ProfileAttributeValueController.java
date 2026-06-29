package vn.vnpost.cdp.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.dto.ProfileAttributeValueCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileAttributeValueUpdateRequest;
import vn.vnpost.cdp.profile.service.ProfileAttributeValueService;

@RestController
@RequestMapping("/v1/admin/profile-attribute-values")
public class ProfileAttributeValueController {

    private final ProfileAttributeValueService service;

    public ProfileAttributeValueController(ProfileAttributeValueService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MethodResult> create(@Valid @RequestBody ProfileAttributeValueCreateRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MethodResult> update(@PathVariable Long id,
                                               @Valid @RequestBody ProfileAttributeValueUpdateRequest request) {
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

    @GetMapping("/by-profile/{masterProfileId}/property/{propertyName}")
    public ResponseEntity<MethodResult> listByProperty(@PathVariable Long masterProfileId,
                                                       @PathVariable String propertyName) {
        return ResponseEntity.ok(MethodResult.success(service.listByProperty(masterProfileId, propertyName)));
    }

    @PatchMapping("/{id}/select")
    public ResponseEntity<MethodResult> selectValue(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.selectValue(id)));
    }

    @PatchMapping("/{id}/unselect")
    public ResponseEntity<MethodResult> unselectValue(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.unselectValue(id)));
    }
}
