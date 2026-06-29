package vn.vnpost.cdp.profile.service.match;

import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.profile.dto.match.ProfileCandidateMergeRequest;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchCandidateResponse;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchCandidateSearchRequest;
import vn.vnpost.cdp.profile.entity.ProfileMatchCandidate;
import vn.vnpost.cdp.profile.entity.ProfileSourceRecord;

import java.util.List;

public interface ProfileMatchCandidateService {

    ProfileMatchCandidateResponse createCandidate(Long leftMasterProfileId, Long rightMasterProfileId);

    ProfileMatchCandidateResponse getById(Long id);

    List<ProfileMatchCandidateResponse> search(ProfileMatchCandidateSearchRequest request);

    List<ProfileMatchCandidateResponse> listPending();

    List<ProfileMatchCandidateResponse> listByStatus(Short status);

    List<ProfileMatchCandidateResponse> listByProfile(Long masterProfileId);

    ProfileMatchCandidateResponse ignore(Long id);

    ProfileMatchCandidateResponse reject(Long id);

    ProfileMatchCandidateResponse merge(Long id, ProfileCandidateMergeRequest request);

    void detectAndCreateCandidatesForProfile(Long masterProfileId);

    /**
     * Directly creates a match candidate between two already-known profiles.
     * Used by CREATE_MATCH_CANDIDATE flow — no database scan needed.
     * Throws if creation fails (caller must not swallow the exception).
     */
    ProfileMatchCandidate createCandidateBetweenProfiles(Long existingProfileId,
                                                         Long newProfileId,
                                                         NormalizedProfileData incomingData,
                                                         ProfileSourceRecord sourceRecord);
}
