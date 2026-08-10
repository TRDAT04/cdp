package vn.vnpost.cdp.profile.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import vn.vnpost.cdp.profile.entity.ProfileMatchReason;

@Repository
public interface ProfileMatchReasonRepository extends ReactiveCrudRepository<ProfileMatchReason, Long> {

    Flux<ProfileMatchReason> findByMatchCandidateId(Long matchCandidateId);
}
