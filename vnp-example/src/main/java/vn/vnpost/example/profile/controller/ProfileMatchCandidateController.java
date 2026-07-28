package vn.vnpost.example.profile.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import vn.vnpost.example.common.response.MethodResult;
import vn.vnpost.example.profile.dto.match.ProfileCandidateMergeRequest;
import vn.vnpost.example.profile.dto.match.ProfileMatchCandidateCreateRequest;
import vn.vnpost.example.profile.dto.match.ProfileMatchCandidateSearchRequest;
import vn.vnpost.example.profile.service.match.ProfileMatchCandidateService;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/profile-match-candidates")
public class ProfileMatchCandidateController {

    private final ProfileMatchCandidateService candidateService;

    public ProfileMatchCandidateController(ProfileMatchCandidateService candidateService) {
        this.candidateService = candidateService;
    }

    /** GET /api/v1/admin/profile-match-candidates */
    @GetMapping
    public Mono<ResponseEntity<MethodResult>> search(@ModelAttribute ProfileMatchCandidateSearchRequest request) {
        log.info("GET /api/v1/admin/profile-match-candidates - request={}", request);
        return candidateService.search(request)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    /** GET /api/v1/admin/profile-match-candidates/pending */
    @GetMapping("/pending")
    public Mono<ResponseEntity<MethodResult>> listPending() {
        log.info("GET /api/v1/admin/profile-match-candidates/pending");
        return candidateService.listPending()
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    /** GET /api/v1/admin/profile-match-candidates/status/{status} */
    @GetMapping("/status/{status}")
    public Mono<ResponseEntity<MethodResult>> listByStatus(@PathVariable Short status) {
        log.info("GET /api/v1/admin/profile-match-candidates/status/{}", status);
        return candidateService.listByStatus(status)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    /** GET /api/v1/admin/profile-match-candidates/{id} */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<MethodResult>> getById(@PathVariable Long id) {
        log.info("GET /api/v1/admin/profile-match-candidates/{}", id);
        return candidateService.getById(id)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    /** GET /api/v1/admin/profile-match-candidates/by-profile/{masterProfileId} */
    @GetMapping("/by-profile/{masterProfileId}")
    public Mono<ResponseEntity<MethodResult>> listByProfile(@PathVariable Long masterProfileId) {
        log.info("GET /api/v1/admin/profile-match-candidates/by-profile/{}", masterProfileId);
        return candidateService.listByProfile(masterProfileId)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    /** POST /api/v1/admin/profile-match-candidates — tạo candidate thủ công giữa 2 master profile. */
    @PostMapping
    public Mono<ResponseEntity<MethodResult>> create(@Valid @RequestBody ProfileMatchCandidateCreateRequest request) {
        log.info("POST /api/v1/admin/profile-match-candidates - left={}, right={}",
                request.getLeftMasterProfileId(), request.getRightMasterProfileId());
        return candidateService.createCandidate(request.getLeftMasterProfileId(), request.getRightMasterProfileId())
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    /** POST /api/v1/admin/profile-match-candidates/{id}/merge */
    @PostMapping("/{id}/merge")
    public Mono<ResponseEntity<MethodResult>> merge(@PathVariable Long id,
                                                     @RequestBody ProfileCandidateMergeRequest request) {
        log.info("POST /api/v1/admin/profile-match-candidates/{}/merge", id);
        return candidateService.merge(id, request)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    /** POST /api/v1/admin/profile-match-candidates/{id}/ignore */
    @PostMapping("/{id}/ignore")
    public Mono<ResponseEntity<MethodResult>> ignore(@PathVariable Long id) {
        log.info("POST /api/v1/admin/profile-match-candidates/{}/ignore", id);
        return candidateService.ignore(id)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    /** POST /api/v1/admin/profile-match-candidates/{id}/reject */
    @PostMapping("/{id}/reject")
    public Mono<ResponseEntity<MethodResult>> reject(@PathVariable Long id) {
        log.info("POST /api/v1/admin/profile-match-candidates/{}/reject", id);
        return candidateService.reject(id)
                .map(result -> ResponseEntity.ok(MethodResult.success(result)));
    }

    /**
     * POST /api/v1/admin/profile-match-candidates/detect/{masterProfileId}
     * Kích hoạt phát hiện trùng lặp thủ công cho 1 profile — chờ hoàn tất (không fire-and-forget)
     * trước khi phản hồi, đúng hành vi đồng bộ của bản gốc.
     */
    @PostMapping("/detect/{masterProfileId}")
    public Mono<ResponseEntity<MethodResult>> detect(@PathVariable Long masterProfileId) {
        log.info("POST /api/v1/admin/profile-match-candidates/detect/{}", masterProfileId);
        return candidateService.detectAndCreateCandidatesForProfile(masterProfileId)
                .thenReturn(ResponseEntity.ok(MethodResult.success("Detection triggered for profile: " + masterProfileId)));
    }
}
