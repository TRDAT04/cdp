package vn.vnpost.example.profile.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import vn.vnpost.example.common.response.MethodResult;
import vn.vnpost.example.profile.dto.query.ProfileSearchRequest;
import vn.vnpost.example.profile.service.ProfileQueryService;
import vn.vnpost.example.profile.service.ScoringService;

import java.time.LocalDate;

/**
 * Chỉ mang 13 endpoint đọc/tra cứu profile (đã chuyển từ vnpost_cdp sang WebFlux + R2DBC).
 * Các endpoint khác của {@code MasterProfileController} gốc (POST/PUT create-update,
 * {@code /unomi}, {@code /code/{profileCode}}, {@code /sync-unomi}) nằm ngoài phạm vi
 * chuyển đổi này — thuộc {@code MasterProfileService}, không được port sang project này.
 */
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
    public Mono<ResponseEntity<MethodResult>> searchProfiles(
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

        log.info("GET /api/v1/admin/profiles - keyword={}, customerType={}, customerGroup={}, status={}, segment={}, page={}, size={}",
                keyword, customerType, customerGroup, status, segment, page, size);

        ProfileSearchRequest request = new ProfileSearchRequest();
        request.setKeyword(keyword);
        request.setCustomerType(customerType);
        request.setCustomerGroup(customerGroup);
        request.setStatus(status);
        request.setWarningStatus(warningStatus);
        request.setSourceSystem(sourceSystem);
        request.setSegment(segment);
        if (StringUtils.hasText(fromLastActivityAt)) {
            request.setFromLastActivityAt(LocalDate.parse(fromLastActivityAt));
        }
        if (StringUtils.hasText(toLastActivityAt)) {
            request.setToLastActivityAt(LocalDate.parse(toLastActivityAt));
        }

        Sort pageSort = StringUtils.hasText(sort)
                ? Sort.by(Sort.Direction.DESC, sort)
                : Sort.by(Sort.Direction.DESC, "modified");
        Pageable pageable = PageRequest.of(page, size, pageSort);

        return profileQueryService.searchProfiles(request, pageable)
                .map(result -> ResponseEntity.ok(
                        MethodResult.success(result.getContent(), result.getTotalElements())));
    }

    // =====================================================================
    // Chi tiết hồ sơ tách theo tab
    // =====================================================================

    /** Tab 1: Tổng quan. */
    @GetMapping("/{id}/overview")
    public Mono<ResponseEntity<MethodResult>> getOverview(@PathVariable Long id) {
        log.info("GET /api/v1/admin/profiles/{}/overview", id);
        return profileQueryService.getProfileOverview(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    /** Tab 2: Hồ sơ liên kết. */
    @GetMapping("/{id}/identity-links")
    public Mono<ResponseEntity<MethodResult>> getIdentityLinks(@PathVariable Long id) {
        log.info("GET /api/v1/admin/profiles/{}/identity-links", id);
        return profileQueryService.getProfileIdentityLinks(id)
                .map(links -> ResponseEntity.ok(MethodResult.success(links, (long) links.size())));
    }

    /** Tab 3: Hồ sơ đa nguồn (so sánh field-by-field). */
    @GetMapping("/{id}/multi-source")
    public Mono<ResponseEntity<MethodResult>> getMultiSource(@PathVariable Long id) {
        log.info("GET /api/v1/admin/profiles/{}/multi-source", id);
        return profileQueryService.getProfileMultiSource(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    /** Tab 4: Địa chỉ (dữ liệu hiện có; địa chỉ chi tiết chờ spec). */
    @GetMapping("/{id}/address")
    public Mono<ResponseEntity<MethodResult>> getAddress(@PathVariable Long id) {
        log.info("GET /api/v1/admin/profiles/{}/address", id);
        return profileQueryService.getProfileAddress(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    /** Tab 6: Hành vi số (Unomi). */
    @GetMapping("/{id}/behavior")
    public Mono<ResponseEntity<MethodResult>> getBehavior(@PathVariable Long id) {
        log.info("GET /api/v1/admin/profiles/{}/behavior", id);
        return profileQueryService.getProfileBehavior(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    /** Tab 10: Nhật ký (change logs + lần sync Unomi gần nhất). */
    @GetMapping("/{id}/change-logs")
    public Mono<ResponseEntity<MethodResult>> getChangeLogs(@PathVariable Long id) {
        log.info("GET /api/v1/admin/profiles/{}/change-logs", id);
        return profileQueryService.getProfileChangeLogs(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    /** Tab: Hoạt động theo mảng dịch vụ chính (7 mảng). */
    @GetMapping("/{id}/service-lines")
    public Mono<ResponseEntity<MethodResult>> getServiceLines(@PathVariable Long id) {
        log.info("GET /api/v1/admin/profiles/{}/service-lines", id);
        return profileQueryService.getProfileServiceLines(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    /** Tab: CSKH (tổng hợp khiếu nại — join complaintCreated + complaintResolved). */
    @GetMapping("/{id}/cskh")
    public Mono<ResponseEntity<MethodResult>> getCskh(@PathVariable Long id) {
        log.info("GET /api/v1/admin/profiles/{}/cskh", id);
        return profileQueryService.getProfileCskh(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    /** Tab: Đồng ý dữ liệu (ma trận đồng ý 3 mục đích × 4 kênh từ event consentUpdated). */
    @GetMapping("/{id}/consent")
    public Mono<ResponseEntity<MethodResult>> getConsent(@PathVariable Long id) {
        log.info("GET /api/v1/admin/profiles/{}/consent", id);
        return profileQueryService.getProfileConsent(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    /** Summary card: tổng hợp nhanh thông tin profile (fullName, uid, tags, activeSystems…). */
    @GetMapping("/{id}/summary")
    public Mono<ResponseEntity<MethodResult>> getProfileSummary(@PathVariable Long id) {
        log.info("GET /api/v1/admin/profiles/{}/summary", id);
        return profileQueryService.getProfileSummary(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    /** Tab: Điểm số & Phân khúc (RFM percentile / CLV / churn / engagement). */
    @GetMapping("/{id}/scoring")
    public Mono<ResponseEntity<MethodResult>> getScoring(@PathVariable Long id) {
        log.info("GET /api/v1/admin/profiles/{}/scoring", id);
        return scoringService.getProfileScoring(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }
}
