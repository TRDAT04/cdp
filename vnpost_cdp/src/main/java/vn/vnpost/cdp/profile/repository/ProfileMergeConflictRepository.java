package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileMergeConflict;

import java.util.List;

@Repository
public interface ProfileMergeConflictRepository extends JpaRepository<ProfileMergeConflict, Long> {

    List<ProfileMergeConflict> findByMasterProfileId(Long masterProfileId);
    List<ProfileMergeConflict> findByMasterProfileIdAndResolutionStatus(Long masterProfileId, Short resolutionStatus);
    boolean existsByMasterProfileIdAndResolutionStatus(Long masterProfileId, Short resolutionStatus);

    List<ProfileMergeConflict> findByResolutionStatus(Short resolutionStatus);
}
