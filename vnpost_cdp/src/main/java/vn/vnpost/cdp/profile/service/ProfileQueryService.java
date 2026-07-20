package vn.vnpost.cdp.profile.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.vnpost.cdp.profile.dto.query.ProfileAddressResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileChangeLogsResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileDetailResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileDigitalBehaviorResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileIdentityLinkDetailResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileListItemResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileMultiSourceComparisonResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileOverviewResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileSearchRequest;
import vn.vnpost.cdp.profile.dto.query.ProfileSummaryResponse;

import java.util.List;

public interface ProfileQueryService {
    ProfileDetailResponse getProfileDetail(Long id);
    Page<ProfileListItemResponse> searchProfiles(ProfileSearchRequest request, Pageable pageable);

    // --- Tách theo tab (chỉ các tab hiện có đủ dữ liệu) ---
    ProfileOverviewResponse getProfileOverview(Long id);                         // Tab 1
    List<ProfileIdentityLinkDetailResponse> getProfileIdentityLinks(Long id);    // Tab 2
    ProfileMultiSourceComparisonResponse getProfileMultiSource(Long id);         // Tab 3
    ProfileAddressResponse getProfileAddress(Long id);                           // Tab 4
    ProfileDigitalBehaviorResponse getProfileBehavior(Long id);                  // Tab 6
    ProfileChangeLogsResponse getProfileChangeLogs(Long id);                     // Tab 10
    ProfileSummaryResponse getProfileSummary(Long id);                           // Summary card
}
