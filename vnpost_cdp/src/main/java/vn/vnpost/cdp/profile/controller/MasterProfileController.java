package vn.vnpost.cdp.profile.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.common.response.MethodResult;

import vn.vnpost.cdp.profile.dto.query.ProfileSearchRequest;
import vn.vnpost.cdp.profile.service.ProfileQueryService;
import vn.vnpost.cdp.profile.service.ScoringService;
import vn.vnpost.shared.sercurity.CheckPermission;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/profiles")
public class MasterProfileController {

    private final ProfileQueryService profileQueryService;
    private final ScoringService scoringService;

    public MasterProfileController(ProfileQueryService profileQueryService,
                                   ScoringService scoringService) {
        this.profileQueryService = profileQueryService;
        this.scoringService = scoringService;
    }

    @GetMapping
    @CheckPermission(index = 1, title = "Xem danh sách Profile")
    public Mono<ResponseEntity> searchProfiles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String customerGroup,
            @RequestParam(required = false) Short status,
            @RequestParam(required = false) String warningStatus,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String fromLastActivityAt,
            @RequestParam(required = false) String toLastActivityAt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {

        ProfileSearchRequest request = new ProfileSearchRequest();
        request.setKeyword(keyword);
        request.setCustomerType(customerType);
        request.setCustomerGroup(customerGroup);
        request.setStatus(status);
        request.setWarningStatus(warningStatus);
        request.setSourceSystem(sourceSystem);
        request.setSegment(segment);

        if (StringUtils.hasText(fromLastActivityAt))
            request.setFromLastActivityAt(LocalDate.parse(fromLastActivityAt));

        if (StringUtils.hasText(toLastActivityAt))
            request.setToLastActivityAt(LocalDate.parse(toLastActivityAt));

        Sort pageSort = StringUtils.hasText(sort)
                ? Sort.by(Sort.Direction.DESC, sort)
                : Sort.by(Sort.Direction.DESC, "modified");

        Pageable pageable = PageRequest.of(page, size, pageSort);

        return profileQueryService.searchProfiles(request, pageable)
                .map(result -> ResponseEntity.ok(
                        MethodResult.success(result.getContent(), result.getTotalElements())));
    }

    @GetMapping("/{id}/overview")
    @CheckPermission(index = 2, title = "Xem tổng quan Profile")
    public Mono<ResponseEntity> getOverview(@PathVariable Long id) {
        return profileQueryService.getProfileOverview(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    @GetMapping("/{id}/identity-links")
    @CheckPermission(index = 3, title = "Xem Identity Links")
    public Mono<ResponseEntity> getIdentityLinks(@PathVariable Long id) {
        return profileQueryService.getProfileIdentityLinks(id)
                .map(links -> ResponseEntity.ok(MethodResult.success(links, (long) links.size())));
    }

    @GetMapping("/{id}/multi-source")
    @CheckPermission(index = 4, title = "Xem Profile đa nguồn")
    public Mono<ResponseEntity> getMultiSource(@PathVariable Long id) {
        return profileQueryService.getProfileMultiSource(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    @GetMapping("/{id}/address")
    @CheckPermission(index = 5, title = "Xem địa chỉ Profile")
    public Mono<ResponseEntity> getAddress(@PathVariable Long id) {
        return profileQueryService.getProfileAddress(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    @GetMapping("/{id}/behavior")
    @CheckPermission(index = 6, title = "Xem hành vi Profile")
    public Mono<ResponseEntity> getBehavior(@PathVariable Long id) {
        return profileQueryService.getProfileBehavior(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    @GetMapping("/{id}/change-logs")
    @CheckPermission(index = 7, title = "Xem lịch sử thay đổi Profile")
    public Mono<ResponseEntity> getChangeLogs(@PathVariable Long id) {
        return profileQueryService.getProfileChangeLogs(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    @GetMapping("/{id}/service-lines")
    @CheckPermission(index = 8, title = "Xem hoạt động dịch vụ Profile")
    public Mono<ResponseEntity> getServiceLines(@PathVariable Long id) {
        return profileQueryService.getProfileServiceLines(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    @GetMapping("/{id}/cskh")
    @CheckPermission(index = 9, title = "Xem CSKH Profile")
    public Mono<ResponseEntity> getCskh(@PathVariable Long id) {
        return profileQueryService.getProfileCskh(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    @GetMapping("/{id}/consent")
    @CheckPermission(index = 10, title = "Xem Consent Profile")
    public Mono<ResponseEntity> getConsent(@PathVariable Long id) {
        return profileQueryService.getProfileConsent(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    @GetMapping("/{id}/summary")
    @CheckPermission(index = 11, title = "Xem tổng hợp Profile")
    public Mono<ResponseEntity> getProfileSummary(@PathVariable Long id) {
        return profileQueryService.getProfileSummary(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    @GetMapping("/{id}/scoring")
    @CheckPermission(index = 12, title = "Xem điểm số Profile")
    public Mono<ResponseEntity> getScoring(@PathVariable Long id) {
        return scoringService.getProfileScoring(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }
}
