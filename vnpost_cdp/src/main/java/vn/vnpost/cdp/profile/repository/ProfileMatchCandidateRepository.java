package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.vnpost.cdp.profile.entity.ProfileMatchCandidate;

import java.util.List;
import java.util.Optional;

public interface ProfileMatchCandidateRepository
        extends JpaRepository<ProfileMatchCandidate, Long>,
                JpaSpecificationExecutor<ProfileMatchCandidate> {

    List<ProfileMatchCandidate> findByStatusOrderByMatchScoreDescCreatedDesc(Short status);

    List<ProfileMatchCandidate> findByLeftMasterProfileIdOrRightMasterProfileId(
            Long leftMasterProfileId, Long rightMasterProfileId);

    boolean existsByLeftMasterProfileIdAndRightMasterProfileIdAndStatus(
            Long leftMasterProfileId, Long rightMasterProfileId, Short status);

    boolean existsByRightMasterProfileIdAndLeftMasterProfileIdAndStatus(
            Long rightMasterProfileId, Long leftMasterProfileId, Short status);

    List<ProfileMatchCandidate> findByMatchLevelAndStatus(String matchLevel, Short status);

    List<ProfileMatchCandidate> findByStatus(Short status);

    Optional<ProfileMatchCandidate> findTopByLeftMasterProfileIdAndRightMasterProfileIdOrderByCreatedDesc(
            Long leftMasterProfileId, Long rightMasterProfileId);

    Optional<ProfileMatchCandidate> findTopByRightMasterProfileIdAndLeftMasterProfileIdOrderByCreatedDesc(
            Long rightMasterProfileId, Long leftMasterProfileId);

    @Query("""
        select count(c) > 0
        from ProfileMatchCandidate c
        where c.status in (0, 1)
          and (
                (c.leftMasterProfileId = :leftId and c.rightMasterProfileId = :rightId)
             or (c.leftMasterProfileId = :rightId and c.rightMasterProfileId = :leftId)
          )
    """)
    boolean existsPendingOrMergedBetween(@Param("leftId") Long leftId,
                                         @Param("rightId") Long rightId);

    @Query("""
        select c
        from ProfileMatchCandidate c
        where (
                (c.leftMasterProfileId = :leftId and c.rightMasterProfileId = :rightId)
             or (c.leftMasterProfileId = :rightId and c.rightMasterProfileId = :leftId)
        )
        order by c.created desc
    """)
    List<ProfileMatchCandidate> findBetween(@Param("leftId") Long leftId,
                                            @Param("rightId") Long rightId);

    @Query("""
        select count(c) > 0
        from ProfileMatchCandidate c
        where c.status = :status
          and (c.leftMasterProfileId = :profileId or c.rightMasterProfileId = :profileId)
    """)
    boolean existsPendingCandidateForProfile(@Param("profileId") Long profileId,
                                             @Param("status") Short status);
}
