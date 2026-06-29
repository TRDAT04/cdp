package vn.vnpost.cdp.profile.service;

import vn.vnpost.cdp.profile.dto.ProfileMergeConflictCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeConflictResolveRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeConflictResponse;

import java.util.List;

public interface ProfileMergeConflictService {

    ProfileMergeConflictResponse create(ProfileMergeConflictCreateRequest request);

    ProfileMergeConflictResponse getById(Long id);

    List<ProfileMergeConflictResponse> listByMasterProfileId(Long masterProfileId);

    List<ProfileMergeConflictResponse> listByResolutionStatus(Short resolutionStatus);

    ProfileMergeConflictResponse resolve(Long id, ProfileMergeConflictResolveRequest request);

    ProfileMergeConflictResponse reject(Long id);

    ProfileMergeConflictResponse ignore(Long id);
}
