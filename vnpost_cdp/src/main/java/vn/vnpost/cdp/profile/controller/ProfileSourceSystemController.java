package vn.vnpost.cdp.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.dto.ProfileSourceSystemCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileSourceSystemUpdateRequest;
import vn.vnpost.cdp.profile.service.ProfileSourceSystemService;

@RestController
@RequestMapping("/api/v1/admin/profile-source-systems")
public class ProfileSourceSystemController {

    private final ProfileSourceSystemService service;

    public ProfileSourceSystemController(ProfileSourceSystemService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MethodResult> create(@Valid @RequestBody ProfileSourceSystemCreateRequest request) {
        return ResponseEntity.status(201).body(MethodResult.success(service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MethodResult> update(@PathVariable Long id,
                                               @RequestBody ProfileSourceSystemUpdateRequest request) {
        return ResponseEntity.ok(MethodResult.success(service.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MethodResult> getById(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(service.getById(id)));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<MethodResult> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(MethodResult.success(service.getByCode(code)));
    }

    @GetMapping
    public ResponseEntity<MethodResult> listAll() {
        return ResponseEntity.ok(MethodResult.success(service.listAll()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<MethodResult> changeStatus(@PathVariable Long id,
                                                     @RequestParam Short status) {
        return ResponseEntity.ok(MethodResult.success(service.changeStatus(id, status)));
    }
}
