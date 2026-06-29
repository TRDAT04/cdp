package vn.vnpost.cdp.profile.service;

import vn.vnpost.cdp.profile.dto.ProfileIdentityLinkCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileIdentityLinkResponse;
import vn.vnpost.cdp.profile.dto.ProfileIdentityLinkUpdateRequest;

import java.util.List;

public interface ProfileIdentityLinkService {
    ProfileIdentityLinkResponse create(ProfileIdentityLinkCreateRequest request);
    ProfileIdentityLinkResponse update(Long id, ProfileIdentityLinkUpdateRequest request);
    ProfileIdentityLinkResponse getById(Long id);
    List<ProfileIdentityLinkResponse> listByMasterProfileId(Long masterProfileId);
    List<ProfileIdentityLinkResponse> listByIdentity(String identityType, String identityValue);
    ProfileIdentityLinkResponse deactivate(Long id);
    ProfileIdentityLinkResponse changeStatus(Long id, Short status);
}
