package vn.vnpost.cdp.profile.service;

import vn.vnpost.cdp.profile.dto.ProfileChangeLogCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileChangeLogResponse;

import java.util.List;

public interface ProfileChangeLogService {

    ProfileChangeLogResponse create(ProfileChangeLogCreateRequest request);

    ProfileChangeLogResponse getById(Long id);

    List<ProfileChangeLogResponse> listByMasterProfileId(Long masterProfileId);

    List<ProfileChangeLogResponse> listByPropertyName(Long masterProfileId, String propertyName);
}
