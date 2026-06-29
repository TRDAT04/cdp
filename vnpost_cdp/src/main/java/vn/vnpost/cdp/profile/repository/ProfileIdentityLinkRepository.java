package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileIdentityLink;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileIdentityLinkRepository extends JpaRepository<ProfileIdentityLink, Long> {
    List<ProfileIdentityLink> findByMasterProfileId(Long masterProfileId);
    List<ProfileIdentityLink> findByMasterProfileIdAndStatus(Long masterProfileId, Short status);
    Optional<ProfileIdentityLink> findBySourceSystemAndSourceCustomerId(String sourceSystem, String sourceCustomerId);
    Optional<ProfileIdentityLink> findBySourceSystemAndSourceCustomerIdAndStatus(String sourceSystem, String sourceCustomerId, Short status);
    List<ProfileIdentityLink> findByIdentityTypeAndIdentityValue(String identityType, String identityValue);
}
