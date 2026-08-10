package vn.vnpost.cdp.profile.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.profile.entity.ProfileMergeRule;

@Repository
public interface ProfileMergeRuleRepository extends ReactiveCrudRepository<ProfileMergeRule, Long> {

    Mono<ProfileMergeRule> findByPropertyNameAndSourceSystemAndStatus(String propertyName, String sourceSystem,
                                                                       Short status);
}
