package vn.vnpost.cdp.profile.service;

import vn.vnpost.cdp.profile.dto.MasterProfileCreateRequest;
import vn.vnpost.cdp.profile.dto.MasterProfileResponse;
import vn.vnpost.cdp.profile.dto.MasterProfileUpdateRequest;

public interface MasterProfileService {

    MasterProfileResponse create(MasterProfileCreateRequest request);

    MasterProfileResponse update(Long id, MasterProfileUpdateRequest request);

    MasterProfileResponse getById(Long id);

    MasterProfileResponse getByProfileCode(String profileCode);

    MasterProfileResponse syncToUnomi(Long id);
}
