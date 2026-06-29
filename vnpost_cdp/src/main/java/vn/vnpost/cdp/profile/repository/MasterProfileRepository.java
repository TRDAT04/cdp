package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.MasterProfile;

import java.util.Optional;

@Repository
public interface MasterProfileRepository extends JpaRepository<MasterProfile, Long>,
        JpaSpecificationExecutor<MasterProfile> {

    Optional<MasterProfile> findByProfileCode(String profileCode);

    Optional<MasterProfile> findByPhone(String phone);

    Optional<MasterProfile> findByEmail(String email);

    boolean existsByProfileCode(String profileCode);

    Optional<MasterProfile> findByIdentityNo(String identityNo);
}
