package vn.vnpost.cdp.profile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.common.utils.IdentityUtils;
import vn.vnpost.cdp.profile.dto.MasterProfileCreateRequest;
import vn.vnpost.cdp.profile.dto.MasterProfileResponse;
import vn.vnpost.cdp.profile.dto.MasterProfileUpdateRequest;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.repository.MasterProfileRepository;
import vn.vnpost.cdp.unomi.dto.UnomiProfileResponse;
import vn.vnpost.cdp.unomi.dto.UnomiProfileSearchResponse;
import vn.vnpost.cdp.unomi.service.UnomiService;

import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional(readOnly = true)
public class MasterProfileServiceImpl implements MasterProfileService {

    private final MasterProfileRepository masterProfileRepository;
    private final UnomiService unomiService;

    public MasterProfileServiceImpl(MasterProfileRepository masterProfileRepository,
                                    UnomiService unomiService) {
        this.masterProfileRepository = masterProfileRepository;
        this.unomiService = unomiService;

    }
    @Override
    public UnomiProfileSearchResponse getProfilesFromUnomi(Integer page,
                                                           Integer size) {
        int offset = page * size;
        return unomiService.getProfiles(offset, size)
                .block();
    }
    @Override
    public UnomiProfileResponse getProfileByItemId(String itemId) {

        return unomiService.getProfileByItemId(itemId)
                .block();
    }

    @Override
    @Transactional
    public MasterProfileResponse create(MasterProfileCreateRequest request) {
        log.info("MasterProfileService - create: profileCode={}", request.getProfileCode());

        if (masterProfileRepository.existsByProfileCode(request.getProfileCode())) {
            throw new BusinessException("PROFILE_CODE_DUPLICATED",
                    "Profile code already exists: " + request.getProfileCode());
        }

        MasterProfile profile = new MasterProfile();
        profile.setProfileCode(request.getProfileCode());
        profile.setFullName(request.getFullName());
        // Chuẩn hoá y như luồng ingest: hồ sơ tạo tay mà lưu "+84 912 345 678" thì pool candidate
        // (query bằng dạng chuẩn "0912345678") sẽ không bao giờ tìm thấy nó.
        profile.setPhone(IdentityUtils.normalizePhone(request.getPhone()));
        profile.setEmail(IdentityUtils.normalizeEmail(request.getEmail()));
        profile.setGender(request.getGender());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setIdentityNo(IdentityUtils.normalizeIdentityNo(request.getIdentityNo()));
        profile.setCustomerType(request.getCustomerType());
        profile.setProvinceCode(request.getProvinceCode());
        profile.setProvinceName(request.getProvinceName());
        profile.setUnitCode(request.getUnitCode());
        profile.setUnitName(request.getUnitName());
        profile.setStatus((short) 1);

        MasterProfile saved = masterProfileRepository.save(profile);
        log.info("MasterProfileService - create success: id={}, profileCode={}",
                saved.getId(), saved.getProfileCode());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MasterProfileResponse update(Long id, MasterProfileUpdateRequest request) {
        log.info("MasterProfileService - update: id={}", id);

        MasterProfile profile = masterProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException("PROFILE_NOT_FOUND",
                        "Profile not found with id: " + id));

        if (request.getFullName() != null) {
            profile.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            profile.setPhone(IdentityUtils.normalizePhone(request.getPhone()));
        }
        if (request.getEmail() != null) {
            profile.setEmail(IdentityUtils.normalizeEmail(request.getEmail()));
        }
        if (request.getGender() != null) {
            profile.setGender(request.getGender());
        }
        if (request.getDateOfBirth() != null) {
            profile.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getIdentityNo() != null) {
            profile.setIdentityNo(IdentityUtils.normalizeIdentityNo(request.getIdentityNo()));
        }
        if (request.getCustomerType() != null) {
            profile.setCustomerType(request.getCustomerType());
        }
        if (request.getProvinceCode() != null) {
            profile.setProvinceCode(request.getProvinceCode());
        }
        if (request.getProvinceName() != null) {
            profile.setProvinceName(request.getProvinceName());
        }
        if (request.getUnitCode() != null) {
            profile.setUnitCode(request.getUnitCode());
        }
        if (request.getUnitName() != null) {
            profile.setUnitName(request.getUnitName());
        }

        MasterProfile saved = masterProfileRepository.save(profile);
        log.info("MasterProfileService - update success: id={}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public MasterProfileResponse getById(Long id) {
        log.info("MasterProfileService - getById: id={}", id);
        MasterProfile profile = masterProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException("PROFILE_NOT_FOUND",
                        "Profile not found with id: " + id));
        return toResponse(profile);
    }

    @Override
    public MasterProfileResponse getByProfileCode(String profileCode) {
        log.info("MasterProfileService - getByProfileCode: profileCode={}", profileCode);
        MasterProfile profile = masterProfileRepository.findByProfileCode(profileCode)
                .orElseThrow(() -> new BusinessException("PROFILE_NOT_FOUND",
                        "Profile not found with profileCode: " + profileCode));
        return toResponse(profile);
    }

    @Override
    @Transactional
    public MasterProfileResponse syncToUnomi(Long id) {
        log.info("MasterProfileService - syncToUnomi: id={}", id);

        MasterProfile profile = masterProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException("PROFILE_NOT_FOUND",
                        "Profile not found with id: " + id));

        unomiService.syncProfileToUnomi(profile).block();

        profile.setSyncedToUnomiAt(LocalDateTime.now());
        MasterProfile saved = masterProfileRepository.save(profile);

        log.info("MasterProfileService - syncToUnomi success: id={}, profileCode={}",
                saved.getId(), saved.getProfileCode());
        return toResponse(saved);
    }

    private MasterProfileResponse toResponse(MasterProfile profile) {
        return MasterProfileResponse.builder()
                .id(profile.getId())
                .profileCode(profile.getProfileCode())
                .fullName(profile.getFullName())
                .phone(profile.getPhone())
                .email(profile.getEmail())
                .gender(profile.getGender())
                .dateOfBirth(profile.getDateOfBirth())
                .identityNo(profile.getIdentityNo())
                .customerType(profile.getCustomerType())
                .provinceCode(profile.getProvinceCode())
                .provinceName(profile.getProvinceName())
                .unitCode(profile.getUnitCode())
                .unitName(profile.getUnitName())
                .sourceSummary(profile.getSourceSummary())
                .lastMergedAt(profile.getLastMergedAt())
                .syncedToUnomiAt(profile.getSyncedToUnomiAt())
                .mergedIntoProfileId(profile.getMergedIntoProfileId())
                .status(profile.getStatus())
                .createdBy(profile.getCreatedBy())
                .created(profile.getCreated())
                .modified(profile.getModified())
                .modifiedBy(profile.getModifiedBy())
                .build();
    }
}
