package vn.vnpost.cdp.profile.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.dto.MasterProfileCreateRequest;
import vn.vnpost.cdp.profile.dto.MasterProfileResponse;
import vn.vnpost.cdp.profile.dto.MasterProfileUpdateRequest;
import vn.vnpost.cdp.profile.dto.query.ProfileDetailResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileListItemResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileSearchRequest;
import vn.vnpost.cdp.profile.service.MasterProfileService;
import vn.vnpost.cdp.profile.service.ProfileQueryService;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/v1/admin/profiles")
public class MasterProfileController {

    private final MasterProfileService masterProfileService;
    private final ProfileQueryService profileQueryService;

    public MasterProfileController(MasterProfileService masterProfileService,
                                   ProfileQueryService profileQueryService) {
        this.masterProfileService = masterProfileService;
        this.profileQueryService = profileQueryService;
    }

    @GetMapping("/unomi")
    public ResponseEntity<MethodResult> getProfilesFromUnomi(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        return ResponseEntity.ok(
                MethodResult.success(
                        masterProfileService.getProfilesFromUnomi(page, size)
                )
        );
    }
    @GetMapping("/unomi/{itemId}")
    public ResponseEntity<MethodResult> getProfileFromUnomi(
            @PathVariable String itemId) {

        return ResponseEntity.ok(
                MethodResult.success(
                        masterProfileService.getProfileByItemId(itemId)
                )
        );
    }
    @PostMapping
    public ResponseEntity<MethodResult> create(
            @Valid @RequestBody MasterProfileCreateRequest request) {
        log.info("POST /v1/admin/profiles - profileCode={}", request.getProfileCode());
        MasterProfileResponse response = masterProfileService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MethodResult.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MethodResult> update(
            @PathVariable Long id,
            @Valid @RequestBody MasterProfileUpdateRequest request) {
        log.info("PUT /v1/admin/profiles/{}", id);
        MasterProfileResponse response = masterProfileService.update(id, request);
        return ResponseEntity.ok(MethodResult.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MethodResult> getById(@PathVariable Long id) {
        log.info("GET /v1/admin/profiles/{}", id);
        MasterProfileResponse response = masterProfileService.getById(id);
        return ResponseEntity.ok(MethodResult.success(response));
    }

    @GetMapping("/code/{profileCode}")
    public ResponseEntity<MethodResult> getByProfileCode(@PathVariable String profileCode) {
        log.info("GET /v1/admin/profiles/code/{}", profileCode);
        MasterProfileResponse response = masterProfileService.getByProfileCode(profileCode);
        return ResponseEntity.ok(MethodResult.success(response));
    }

    @PostMapping("/{id}/sync-unomi")
    public ResponseEntity<MethodResult> syncToUnomi(@PathVariable Long id) {
        log.info("POST /v1/admin/profiles/{}/sync-unomi", id);
        MasterProfileResponse response = masterProfileService.syncToUnomi(id);
        return ResponseEntity.ok(MethodResult.success(response));
    }

    @GetMapping
    public ResponseEntity<MethodResult> searchProfiles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) Short status,
            @RequestParam(required = false) String warningStatus,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String fromLastActivityAt,
            @RequestParam(required = false) String toLastActivityAt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {

        log.info("GET /v1/admin/profiles - keyword={}, customerType={}, status={}, page={}, size={}",
                keyword, customerType, status, page, size);

        ProfileSearchRequest request = new ProfileSearchRequest();
        request.setKeyword(keyword);
        request.setCustomerType(customerType);
        request.setStatus(status);
        request.setWarningStatus(warningStatus);
        request.setSourceSystem(sourceSystem);
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

        Page<ProfileListItemResponse> result = profileQueryService.searchProfiles(request, pageable);
        return ResponseEntity.ok(MethodResult.success(result));
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<MethodResult> getDetail(@PathVariable Long id) {
        log.info("GET /v1/admin/profiles/{}/detail", id);
        ProfileDetailResponse response = profileQueryService.getProfileDetail(id);
        return ResponseEntity.ok(MethodResult.success(response));
    }
}
