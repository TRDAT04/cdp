package vn.vnpost.cdp.profile.service;

import vn.vnpost.cdp.profile.dto.ProfileMergeJobCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeJobResponse;
import vn.vnpost.cdp.profile.dto.ProfileMergeJobUpdateRequest;

import java.util.List;

public interface ProfileMergeJobService {

    ProfileMergeJobResponse create(ProfileMergeJobCreateRequest request);

    ProfileMergeJobResponse getById(Long id);

    List<ProfileMergeJobResponse> listByJobType(String jobType);

    List<ProfileMergeJobResponse> listByStatus(Short status);

    ProfileMergeJobResponse markRunning(Long id);

    ProfileMergeJobResponse markSuccess(Long id, ProfileMergeJobUpdateRequest request);

    ProfileMergeJobResponse markFailed(Long id, String errorMessage);

    ProfileMergeJobResponse markPartialSuccess(Long id, ProfileMergeJobUpdateRequest request);
}
