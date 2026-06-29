package vn.vnpost.cdp.profile.service;

import vn.vnpost.cdp.profile.dto.ProfileSourceRecordCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileSourceRecordResponse;
import vn.vnpost.cdp.profile.dto.ProfileSourceRecordUpdateRequest;

import java.util.List;

public interface ProfileSourceRecordService {
    ProfileSourceRecordResponse create(ProfileSourceRecordCreateRequest request);
    ProfileSourceRecordResponse update(Long id, ProfileSourceRecordUpdateRequest request);
    ProfileSourceRecordResponse getById(Long id);
    List<ProfileSourceRecordResponse> listByMasterProfileId(Long masterProfileId);
    List<ProfileSourceRecordResponse> listBySourceSystem(String sourceSystem);
    List<ProfileSourceRecordResponse> listByMergeStatus(Short mergeStatus);
    ProfileSourceRecordResponse markProcessed(Long id);
    ProfileSourceRecordResponse markError(Long id, String errorMessage);
}
