package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileMergeRequest;

import java.util.List;

@Repository
public interface ProfileMergeRequestRepository extends JpaRepository<ProfileMergeRequest, Long> {

    List<ProfileMergeRequest> findBySourceMasterProfileId(Long sourceMasterProfileId);

    List<ProfileMergeRequest> findByTargetMasterProfileId(Long targetMasterProfileId);

    List<ProfileMergeRequest> findByStatus(Short status);
}
