package vn.vnpost.cdp.profile.service;

import vn.vnpost.cdp.profile.dto.ProfileMergeRequestApproveRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeRequestCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeRequestResponse;

import java.util.List;

public interface ProfileMergeRequestService {

    ProfileMergeRequestResponse create(ProfileMergeRequestCreateRequest request);

    ProfileMergeRequestResponse getById(Long id);

    List<ProfileMergeRequestResponse> listByStatus(Short status);

    List<ProfileMergeRequestResponse> listBySourceProfile(Long sourceMasterProfileId);

    List<ProfileMergeRequestResponse> listByTargetProfile(Long targetMasterProfileId);

    ProfileMergeRequestResponse approve(Long id, ProfileMergeRequestApproveRequest request);

    ProfileMergeRequestResponse reject(Long id);

    ProfileMergeRequestResponse complete(Long id);

    ProfileMergeRequestResponse fail(Long id, String errorMessage);
}
