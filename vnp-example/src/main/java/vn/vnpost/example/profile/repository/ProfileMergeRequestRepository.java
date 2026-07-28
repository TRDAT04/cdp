package vn.vnpost.example.profile.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.example.profile.entity.ProfileMergeRequest;

/**
 * {@code ProfileMatchCandidateServiceImpl.merge()} chỉ dùng {@code .save()} (kế thừa từ
 * {@link ReactiveCrudRepository}) — các method tra cứu khác của repository gốc
 * (findBySourceMasterProfileId, findByTargetMasterProfileId, findByStatus) phục vụ
 * {@code ProfileMergeRequestService}/{@code ProfileMergeRequestController} (luồng approve/reject
 * merge request riêng) — nằm ngoài phạm vi chuyển đổi lần này nên không port.
 */
@Repository
public interface ProfileMergeRequestRepository extends ReactiveCrudRepository<ProfileMergeRequest, Long> {
}
