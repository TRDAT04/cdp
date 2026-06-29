package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileMergeRule;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileMergeRuleRepository extends JpaRepository<ProfileMergeRule, Long> {
    List<ProfileMergeRule> findByPropertyNameAndStatusOrderByPriorityAsc(String propertyName, Short status);
    Optional<ProfileMergeRule> findByPropertyNameAndSourceSystemAndStatus(String propertyName, String sourceSystem, Short status);
    List<ProfileMergeRule> findByStatus(Short status);
}
