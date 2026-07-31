package vn.vnpost.cdp.profile.service.match;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.vnpost.cdp.ingestion.dto.NormalizedProfileData;
import vn.vnpost.cdp.profile.dto.match.ProfileCandidateMergeRequest;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchCandidateResponse;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchCandidateSearchRequest;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchGroupResponse;
import vn.vnpost.cdp.profile.dto.match.ProfileMatchGroupSearchRequest;
import vn.vnpost.cdp.profile.entity.ProfileMatchCandidate;
import vn.vnpost.cdp.profile.entity.ProfileSourceRecord;

import java.util.List;

public interface ProfileMatchCandidateService {

    ProfileMatchCandidateResponse createCandidate(Long leftMasterProfileId, Long rightMasterProfileId);

    ProfileMatchCandidateResponse getById(Long id);

    List<ProfileMatchCandidateResponse> search(ProfileMatchCandidateSearchRequest request);

    /**
     * Màn "Đối soát định danh": mỗi phần tử là MỘT hồ sơ gốc kèm số liệu tổng hợp của toàn bộ
     * candidate PENDING liên quan tới nó (số mã chờ, điểm tin cậy cao nhất, khoá khớp nổi bật).
     *
     * <p>Khác {@link #search} và {@link #listByStatus} — hai hàm đó trả về từng CẶP candidate và
     * gom dữ liệu ở tầng Java; hàm này GROUP BY ngay ở DB và phân trang trên tập đã gom.
     */
    Page<ProfileMatchGroupResponse> searchPendingGroups(ProfileMatchGroupSearchRequest request,
                                                        Pageable pageable);

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
