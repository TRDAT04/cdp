package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.vnpost.cdp.profile.entity.ProfileMatchReason;

import java.util.List;

public interface ProfileMatchReasonRepository extends JpaRepository<ProfileMatchReason, Long> {

    List<ProfileMatchReason> findByMatchCandidateId(Long matchCandidateId);

    void deleteByMatchCandidateId(Long matchCandidateId);
}
