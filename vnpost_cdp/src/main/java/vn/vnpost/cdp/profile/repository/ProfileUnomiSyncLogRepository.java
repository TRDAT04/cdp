package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileUnomiSyncLog;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileUnomiSyncLogRepository extends JpaRepository<ProfileUnomiSyncLog, Long> {

    List<ProfileUnomiSyncLog> findByMasterProfileIdOrderBySyncedAtDesc(Long masterProfileId);
    Optional<ProfileUnomiSyncLog> findTopByMasterProfileIdOrderBySyncedAtDesc(Long masterProfileId);

    List<ProfileUnomiSyncLog> findByProfileCodeOrderBySyncedAtDesc(String profileCode);

    List<ProfileUnomiSyncLog> findByStatus(Short status);
}
