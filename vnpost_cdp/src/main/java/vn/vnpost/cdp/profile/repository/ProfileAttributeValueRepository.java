package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileAttributeValue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileAttributeValueRepository extends JpaRepository<ProfileAttributeValue, Long> {
    List<ProfileAttributeValue> findByMasterProfileId(Long masterProfileId);

    List<ProfileAttributeValue> findByMasterProfileIdAndPropertyName(Long masterProfileId, String propertyName);

    List<ProfileAttributeValue> findByMasterProfileIdAndIsSelected(Long masterProfileId, Boolean isSelected);

    Optional<ProfileAttributeValue> findTopByMasterProfileIdAndPropertyNameInOrderByReceivedAtDesc(
            Long masterProfileId, Collection<String> propertyNames);

    Optional<ProfileAttributeValue> findFirstByMasterProfileIdAndPropertyNameAndIsSelectedTrue(
            Long masterProfileId, String propertyName);
}
