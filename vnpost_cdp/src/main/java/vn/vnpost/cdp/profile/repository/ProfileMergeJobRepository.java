package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileMergeJob;

import java.util.List;

@Repository
public interface ProfileMergeJobRepository extends JpaRepository<ProfileMergeJob, Long> {

    List<ProfileMergeJob> findByJobType(String jobType);

    List<ProfileMergeJob> findByStatus(Short status);
}
