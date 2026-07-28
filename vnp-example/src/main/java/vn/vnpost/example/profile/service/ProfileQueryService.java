package vn.vnpost.example.profile.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;
import vn.vnpost.example.profile.dto.query.ProfileAddressResponse;
import vn.vnpost.example.profile.dto.query.ProfileChangeLogsResponse;
import vn.vnpost.example.profile.dto.query.ProfileConsentResponse;
import vn.vnpost.example.profile.dto.query.ProfileCskhResponse;
import vn.vnpost.example.profile.dto.query.ProfileDigitalBehaviorResponse;
import vn.vnpost.example.profile.dto.query.ProfileIdentityLinkDetailResponse;
import vn.vnpost.example.profile.dto.query.ProfileListItemResponse;
import vn.vnpost.example.profile.dto.query.ProfileMultiSourceComparisonResponse;
import vn.vnpost.example.profile.dto.query.ProfileOverviewResponse;
import vn.vnpost.example.profile.dto.query.ProfileSearchRequest;
import vn.vnpost.example.profile.dto.query.ProfileServiceLinesResponse;
import vn.vnpost.example.profile.dto.query.ProfileSummaryResponse;

import java.util.List;

public interface ProfileQueryService {
    Mono<Page<ProfileListItemResponse>> searchProfiles(ProfileSearchRequest request, Pageable pageable);

    // --- Tách theo tab (chỉ các tab hiện có đủ dữ liệu) ---
    Mono<ProfileOverviewResponse> getProfileOverview(Long id);                         // Tab 1
    Mono<List<ProfileIdentityLinkDetailResponse>> getProfileIdentityLinks(Long id);    // Tab 2
    Mono<ProfileMultiSourceComparisonResponse> getProfileMultiSource(Long id);         // Tab 3
    Mono<ProfileAddressResponse> getProfileAddress(Long id);                           // Tab 4
    Mono<ProfileDigitalBehaviorResponse> getProfileBehavior(Long id);                  // Tab 6
    Mono<ProfileChangeLogsResponse> getProfileChangeLogs(Long id);                     // Tab 10
    Mono<ProfileServiceLinesResponse> getProfileServiceLines(Long id);                 // Tab: Mảng dịch vụ
    Mono<ProfileCskhResponse> getProfileCskh(Long id);                                 // Tab: CSKH
    Mono<ProfileConsentResponse> getProfileConsent(Long id);                           // Tab: Đồng ý dữ liệu
    Mono<ProfileSummaryResponse> getProfileSummary(Long id);                           // Summary card
}
