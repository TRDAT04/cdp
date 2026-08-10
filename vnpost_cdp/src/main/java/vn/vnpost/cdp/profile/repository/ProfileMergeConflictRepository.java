package vn.vnpost.cdp.profile.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.profile.entity.ProfileMergeConflict;

@Repository
public interface ProfileMergeConflictRepository extends ReactiveCrudRepository<ProfileMergeConflict, Long> {

    Mono<Boolean> existsByMasterProfileIdAndResolutionStatus(Long masterProfileId, Short resolutionStatus);
}
