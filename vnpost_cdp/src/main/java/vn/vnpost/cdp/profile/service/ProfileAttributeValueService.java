package vn.vnpost.cdp.profile.service;

import vn.vnpost.cdp.profile.dto.ProfileAttributeValueCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileAttributeValueResponse;
import vn.vnpost.cdp.profile.dto.ProfileAttributeValueUpdateRequest;

import java.util.List;

public interface ProfileAttributeValueService {

    ProfileAttributeValueResponse create(ProfileAttributeValueCreateRequest request);

    ProfileAttributeValueResponse update(Long id, ProfileAttributeValueUpdateRequest request);

    ProfileAttributeValueResponse getById(Long id);

    List<ProfileAttributeValueResponse> listByMasterProfileId(Long masterProfileId);

    List<ProfileAttributeValueResponse> listByProperty(Long masterProfileId, String propertyName);

    ProfileAttributeValueResponse selectValue(Long id);

    ProfileAttributeValueResponse unselectValue(Long id);
}
