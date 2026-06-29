package vn.vnpost.cdp.profile.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.vnpost.cdp.profile.dto.query.ProfileDetailResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileListItemResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileSearchRequest;

public interface ProfileQueryService {
    ProfileDetailResponse getProfileDetail(Long id);
    Page<ProfileListItemResponse> searchProfiles(ProfileSearchRequest request, Pageable pageable);
}
