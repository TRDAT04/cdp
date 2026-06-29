package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileChangeLog;

import java.util.List;

@Repository
public interface ProfileChangeLogRepository extends JpaRepository<ProfileChangeLog, Long> {
    List<ProfileChangeLog> findByMasterProfileIdOrderByChangedAtDesc(Long masterProfileId);
    List<ProfileChangeLog> findTop20ByMasterProfileIdOrderByChangedAtDesc(Long masterProfileId);
    List<ProfileChangeLog> findByMasterProfileIdAndPropertyNameOrderByChangedAtDesc(Long masterProfileId, String propertyName);
}
