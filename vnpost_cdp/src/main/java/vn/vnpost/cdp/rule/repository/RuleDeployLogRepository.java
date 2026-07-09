package vn.vnpost.cdp.rule.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.rule.entity.RuleDeployLog;

import java.util.List;

@Repository
public interface RuleDeployLogRepository extends JpaRepository<RuleDeployLog, Long> {

    List<RuleDeployLog> findByRuleIdOrderByDeployedAtDesc(String ruleId);

    Page<RuleDeployLog> findAllByOrderByDeployedAtDesc(Pageable pageable);

    List<RuleDeployLog> findByStatus(String status);
}
