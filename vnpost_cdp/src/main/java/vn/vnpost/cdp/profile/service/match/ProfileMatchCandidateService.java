package vn.vnpost.cdp.profile.service.match;

import reactor.core.publisher.Mono;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.profile.dto.match.ProfileCandidateMergeRequest;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchCandidateResponse;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchCandidateSearchRequest;
import vn.vnpost.cdp.profile.entity.ProfileMatchCandidate;
import vn.vnpost.cdp.profile.entity.ProfileSourceRecord;

import java.util.List;

/**
 * Đầy đủ method — bao gồm 2 method dùng bởi luồng ghi ingestion
 * ({@code createCandidateBetweenProfiles}, {@code detectAndCreateCandidatesForProfile}) VÀ 9
 * method CRUD/admin phục vụ {@code ProfileMatchCandidateController}.
 */
public interface ProfileMatchCandidateService {

    Mono<ProfileMatchCandidateResponse> createCandidate(Long leftMasterProfileId, Long rightMasterProfileId);

    Mono<ProfileMatchCandidateResponse> getById(Long id);

    Mono<List<ProfileMatchCandidateResponse>> search(ProfileMatchCandidateSearchRequest request);

    Mono<List<ProfileMatchCandidateResponse>> listPending();

    Mono<List<ProfileMatchCandidateResponse>> listByStatus(Short status);

    Mono<List<ProfileMatchCandidateResponse>> listByProfile(Long masterProfileId);

    Mono<ProfileMatchCandidateResponse> ignore(Long id);

    Mono<ProfileMatchCandidateResponse> reject(Long id);

    Mono<ProfileMatchCandidateResponse> merge(Long id, ProfileCandidateMergeRequest request);

    /**
     * Directly creates a match candidate between two already-known profiles.
     * Used by CREATE_MATCH_CANDIDATE flow — no database scan needed.
     */
    Mono<ProfileMatchCandidate> createCandidateBetweenProfiles(Long existingProfileId,
                                                               Long newProfileId,
                                                               NormalizedProfileData incomingData,
                                                               ProfileSourceRecord sourceRecord);

    Mono<Void> detectAndCreateCandidatesForProfile(Long masterProfileId);
}
