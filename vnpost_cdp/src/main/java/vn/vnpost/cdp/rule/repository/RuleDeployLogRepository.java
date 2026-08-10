package vn.vnpost.cdp.rule.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.rule.entity.RuleDeployLog;

@Repository
public interface RuleDeployLogRepository extends ReactiveCrudRepository<RuleDeployLog, Long> {

    Flux<RuleDeployLog> findByRuleIdOrderByDeployedAtDesc(String ruleId);

    /** Spring Data R2DBC dịch {@code Pageable} thành ORDER BY/LIMIT/OFFSET tự động. */
    Flux<RuleDeployLog> findAllByOrderByDeployedAtDesc(Pageable pageable);

    @Query("SELECT COUNT(*) FROM rule_deploy_logs")
    Mono<Long> countAll();
}
