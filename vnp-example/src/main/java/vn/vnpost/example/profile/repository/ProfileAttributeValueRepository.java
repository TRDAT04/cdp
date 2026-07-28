package vn.vnpost.example.profile.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.vnpost.example.profile.entity.ProfileAttributeValue;

import java.util.Collection;

@Repository
public interface ProfileAttributeValueRepository extends ReactiveCrudRepository<ProfileAttributeValue, Long> {

    Flux<ProfileAttributeValue> findByMasterProfileId(Long masterProfileId);

    Flux<ProfileAttributeValue> findByMasterProfileIdAndPropertyName(Long masterProfileId, String propertyName);

    Mono<ProfileAttributeValue> findFirstByMasterProfileIdAndPropertyNameAndIsSelectedTrue(
            Long masterProfileId, String propertyName);

    @Query("SELECT * FROM profile_attribute_values " +
            "WHERE master_profile_id = :masterProfileId AND property_name IN (:propertyNames) " +
            "ORDER BY received_at DESC LIMIT 1")
    Mono<ProfileAttributeValue> findTopByMasterProfileIdAndPropertyNameInOrderByReceivedAtDesc(
            @Param("masterProfileId") Long masterProfileId,
            @Param("propertyNames") Collection<String> propertyNames);
}
