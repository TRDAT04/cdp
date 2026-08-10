package vn.vnpost.cdp.profile.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.profile.entity.ProfileSourceRecord;

@Repository
public interface ProfileSourceRecordRepository extends ReactiveCrudRepository<ProfileSourceRecord, Long> {

    Flux<ProfileSourceRecord> findByMasterProfileId(Long masterProfileId);

    @Query("SELECT * FROM profile_source_records WHERE source_system = :sourceSystem " +
            "AND source_customer_id = :sourceCustomerId ORDER BY received_at DESC LIMIT 1")
    Mono<ProfileSourceRecord> findFirstBySourceSystemAndSourceCustomerIdOrderByReceivedAtDesc(
            @Param("sourceSystem") String sourceSystem,
            @Param("sourceCustomerId") String sourceCustomerId);
}
