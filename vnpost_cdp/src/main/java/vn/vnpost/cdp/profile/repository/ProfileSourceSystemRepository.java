package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileSourceSystem;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileSourceSystemRepository extends JpaRepository<ProfileSourceSystem, Long> {
    Optional<ProfileSourceSystem> findByCode(String code);
    boolean existsByCode(String code);
    List<ProfileSourceSystem> findByStatus(Short status);
}
