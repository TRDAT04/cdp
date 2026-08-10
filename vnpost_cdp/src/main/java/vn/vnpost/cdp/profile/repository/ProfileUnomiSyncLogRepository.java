package vn.vnpost.cdp.profile.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.profile.entity.ProfileUnomiSyncLog;

@Repository
public interface ProfileUnomiSyncLogRepository extends ReactiveCrudRepository<ProfileUnomiSyncLog, Long> {

    @Query("SELECT * FROM profile_unomi_sync_logs WHERE master_profile_id = :masterProfileId " +
            "ORDER BY synced_at DESC LIMIT 1")
    Mono<ProfileUnomiSyncLog> findTopByMasterProfileIdOrderBySyncedAtDesc(@Param("masterProfileId") Long masterProfileId);
}
