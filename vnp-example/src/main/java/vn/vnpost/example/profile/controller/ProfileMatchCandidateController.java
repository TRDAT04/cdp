package vn.vnpost.example.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import vn.vnpost.example.common.response.MethodResult;
import vn.vnpost.example.profile.dto.match.ProfileCandidateMergeRequest;
import vn.vnpost.example.profile.dto.match.ProfileMatchCandidateCreateRequest;
import vn.vnpost.example.profile.dto.match.ProfileMatchCandidateSearchRequest;
import vn.vnpost.example.profile.service.match.ProfileMatchCandidateService;
import vn.vnpost.shared.sercurity.CheckPermission;

@RestController
@RequestMapping("/api/v1/admin/profile-match-candidates")
public class ProfileMatchCandidateController {

    private final ProfileMatchCandidateService candidateService;

    public ProfileMatchCandidateController(ProfileMatchCandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping
    @CheckPermission(index = 1, title = "Tìm kiếm Match Candidate")
    public Mono<ResponseEntity<MethodResult>> search(
            @ModelAttribute ProfileMatchCandidateSearchRequest request) {
        return candidateService.search(request)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    @GetMapping("/pending")
    @CheckPermission(index = 2, title = "Xem Match Candidate đang chờ xử lý")
    public Mono<ResponseEntity<MethodResult>> listPending() {
        return candidateService.listPending()
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    @GetMapping("/status/{status}")
    @CheckPermission(index = 3, title = "Xem Match Candidate theo trạng thái")
    public Mono<ResponseEntity<MethodResult>> listByStatus(
            @PathVariable Short status) {
        return candidateService.listByStatus(status)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    @GetMapping("/{id}")
    @CheckPermission(index = 4, title = "Xem chi tiết Match Candidate")
    public Mono<ResponseEntity<MethodResult>> getById(
            @PathVariable Long id) {
        return candidateService.getById(id)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    @GetMapping("/by-profile/{masterProfileId}")
    @CheckPermission(index = 5, title = "Xem Match Candidate theo Profile")
    public Mono<ResponseEntity<MethodResult>> listByProfile(
            @PathVariable Long masterProfileId) {
        return candidateService.listByProfile(masterProfileId)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    @PostMapping
    @CheckPermission(index = 6, title = "Tạo Match Candidate")
    public Mono<ResponseEntity<MethodResult>> create(
            @Valid @RequestBody ProfileMatchCandidateCreateRequest request) {
        return candidateService
                .createCandidate(
                        request.getLeftMasterProfileId(),
                        request.getRightMasterProfileId()
                )
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    @PostMapping("/{id}/merge")
    @CheckPermission(index = 7, title = "Merge Match Candidate")
    public Mono<ResponseEntity<MethodResult>> merge(
            @PathVariable Long id,
            @RequestBody ProfileCandidateMergeRequest request) {
        return candidateService.merge(id, request)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    @PostMapping("/{id}/ignore")
    @CheckPermission(index = 8, title = "Ignore Match Candidate")
    public Mono<ResponseEntity<MethodResult>> ignore(
            @PathVariable Long id) {
        return candidateService.ignore(id)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    @PostMapping("/{id}/reject")
    @CheckPermission(index = 9, title = "Reject Match Candidate")
    public Mono<ResponseEntity<MethodResult>> reject(
            @PathVariable Long id) {
        return candidateService.reject(id)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    @PostMapping("/detect/{masterProfileId}")
    @CheckPermission(index = 10, title = "Detect Match Candidate")
    public Mono<ResponseEntity<MethodResult>> detect(
            @PathVariable Long masterProfileId) {
        return candidateService
                .detectAndCreateCandidatesForProfile(masterProfileId)
                .thenReturn(ResponseEntity.ok(
                        MethodResult.success(
                                "Detection triggered for profile: " + masterProfileId
                        )
                ));
    }
}
