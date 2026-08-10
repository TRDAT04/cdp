package vn.vnpost.cdp.profile.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import vn.vnpost.cdp.profile.entity.ProfileChangeLog;

@Repository
public interface ProfileChangeLogRepository extends ReactiveCrudRepository<ProfileChangeLog, Long> {

    Flux<ProfileChangeLog> findByMasterProfileIdOrderByChangedAtDesc(Long masterProfileId);

    @Query("SELECT * FROM profile_change_logs WHERE master_profile_id = :masterProfileId " +
            "ORDER BY changed_at DESC LIMIT 20")
    Flux<ProfileChangeLog> findTop20ByMasterProfileIdOrderByChangedAtDesc(@Param("masterProfileId") Long masterProfileId);
}
