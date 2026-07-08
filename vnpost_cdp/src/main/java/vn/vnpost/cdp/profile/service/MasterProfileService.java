package vn.vnpost.cdp.profile.service;

import vn.vnpost.cdp.profile.dto.MasterProfileCreateRequest;
import vn.vnpost.cdp.profile.dto.MasterProfileResponse;
import vn.vnpost.cdp.profile.dto.MasterProfileUpdateRequest;
import vn.vnpost.cdp.unomi.dto.UnomiProfileResponse;
import vn.vnpost.cdp.unomi.dto.UnomiProfileSearchResponse;

public interface MasterProfileService {

    MasterProfileResponse create(MasterProfileCreateRequest request);

    MasterProfileResponse update(Long id, MasterProfileUpdateRequest request);

    MasterProfileResponse getById(Long id);

    MasterProfileResponse getByProfileCode(String profileCode);

    MasterProfileResponse syncToUnomi(Long id);

    UnomiProfileSearchResponse getProfilesFromUnomi(Integer page,Integer size);

    UnomiProfileResponse getProfileByItemId(String itemId);
}
