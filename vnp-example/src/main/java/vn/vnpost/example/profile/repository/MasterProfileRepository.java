package vn.vnpost.example.profile.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import vn.vnpost.example.profile.entity.MasterProfile;

@Repository
public interface MasterProfileRepository extends ReactiveCrudRepository<MasterProfile, Long> {

    Mono<MasterProfile> findByIdentityNo(String identityNo);

    Mono<MasterProfile> findByPhone(String phone);

    Mono<MasterProfile> findByEmail(String email);

    Mono<MasterProfile> findByTaxCode(String taxCode);
}
