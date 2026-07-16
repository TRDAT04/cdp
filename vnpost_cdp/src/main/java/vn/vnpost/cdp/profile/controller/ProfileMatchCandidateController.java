package vn.vnpost.cdp.profile.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.dto.match.*;
import vn.vnpost.cdp.profile.service.match.ProfileMatchCandidateService;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/profile-match-candidates")
public class ProfileMatchCandidateController {

    private final ProfileMatchCandidateService candidateService;

    public ProfileMatchCandidateController(ProfileMatchCandidateService candidateService) {
        this.candidateService = candidateService;
    }

    /**
     * GET /v1/admin/profile-match-candidates
     * Search with optional filters: status, matchLevel, minScore, sourceSystem, keyword, fromDate, toDate
     */
    @GetMapping
    public ResponseEntity<MethodResult> search(
            @ModelAttribute ProfileMatchCandidateSearchRequest request) {
        return ResponseEntity.ok(MethodResult.success(candidateService.search(request)));
    }

    /**
     * GET /v1/admin/profile-match-candidates/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<MethodResult> listPending() {
        return ResponseEntity.ok(MethodResult.success(candidateService.listPending()));
    }

    /**
     * GET /v1/admin/profile-match-candidates/status/{status}
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<MethodResult> listByStatus(@PathVariable Short status) {
        return ResponseEntity.ok(MethodResult.success(candidateService.listByStatus(status)));
    }

    /**
     * GET /v1/admin/profile-match-candidates/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<MethodResult> getById(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(candidateService.getById(id)));
    }

    /**
     * GET /v1/admin/profile-match-candidates/by-profile/{masterProfileId}
     */
    @GetMapping("/by-profile/{masterProfileId}")
    public ResponseEntity<MethodResult> listByProfile(@PathVariable Long masterProfileId) {
        return ResponseEntity.ok(MethodResult.success(candidateService.listByProfile(masterProfileId)));
    }

    /**
     * POST /v1/admin/profile-match-candidates
     * Manually create a candidate from two master profiles.
     */
    @PostMapping
    public ResponseEntity<MethodResult> create(
            @Valid @RequestBody ProfileMatchCandidateCreateRequest request) {
        return ResponseEntity.ok(MethodResult.success(
                candidateService.createCandidate(
                        request.getLeftMasterProfileId(),
                        request.getRightMasterProfileId())));
    }

    /**
     * POST /v1/admin/profile-match-candidates/{id}/merge
     */
    @PostMapping("/{id}/merge")
    public ResponseEntity<MethodResult> merge(
            @PathVariable Long id,
            @RequestBody ProfileCandidateMergeRequest request) {
        return ResponseEntity.ok(MethodResult.success(candidateService.merge(id, request)));
    }

    /**
     * POST /v1/admin/profile-match-candidates/{id}/ignore
     */
    @PostMapping("/{id}/ignore")
    public ResponseEntity<MethodResult> ignore(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(candidateService.ignore(id)));
    }

    /**
     * POST /v1/admin/profile-match-candidates/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<MethodResult> reject(@PathVariable Long id) {
        return ResponseEntity.ok(MethodResult.success(candidateService.reject(id)));
    }

    /**
     * POST /v1/admin/profile-match-candidates/detect/{masterProfileId}
     * Manually trigger duplicate detection for one profile.
     */
    @PostMapping("/detect/{masterProfileId}")
    public ResponseEntity<MethodResult> detect(@PathVariable Long masterProfileId) {
        candidateService.detectAndCreateCandidatesForProfile(masterProfileId);
        return ResponseEntity.ok(MethodResult.success("Detection triggered for profile: " + masterProfileId));
    }
}
