package vn.vnpost.cdp.profile.service;

import vn.vnpost.cdp.profile.dto.ProfileUnomiSyncLogCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileUnomiSyncLogResponse;

import java.util.List;
import java.util.Map;

public interface ProfileUnomiSyncLogService {

    ProfileUnomiSyncLogResponse create(ProfileUnomiSyncLogCreateRequest request);

    ProfileUnomiSyncLogResponse getById(Long id);

    List<ProfileUnomiSyncLogResponse> listByMasterProfileId(Long masterProfileId);

    List<ProfileUnomiSyncLogResponse> listByProfileCode(String profileCode);

    List<ProfileUnomiSyncLogResponse> listByStatus(Short status);

    ProfileUnomiSyncLogResponse markSuccess(Long id, Map<String, Object> responsePayload);

    ProfileUnomiSyncLogResponse markFailed(Long id, String errorMessage);

    ProfileUnomiSyncLogResponse markRetrying(Long id);
}
