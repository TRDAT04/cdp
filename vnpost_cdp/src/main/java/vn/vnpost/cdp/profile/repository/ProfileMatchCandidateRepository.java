package vn.vnpost.cdp.profile.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.profile.entity.ProfileMatchCandidate;

@Repository
public interface ProfileMatchCandidateRepository extends ReactiveCrudRepository<ProfileMatchCandidate, Long> {

    @Query("SELECT COUNT(*) > 0 FROM profile_match_candidates " +
            "WHERE status = :status AND (left_master_profile_id = :profileId OR right_master_profile_id = :profileId)")
    Mono<Boolean> existsPendingCandidateForProfile(@Param("profileId") Long profileId,
                                                    @Param("status") Short status);

    Mono<Boolean> existsByLeftMasterProfileIdAndRightMasterProfileIdAndStatus(
            Long leftMasterProfileId, Long rightMasterProfileId, Short status);

    Mono<Boolean> existsByRightMasterProfileIdAndLeftMasterProfileIdAndStatus(
            Long rightMasterProfileId, Long leftMasterProfileId, Short status);

    @Query("""
            SELECT count(*) > 0 FROM profile_match_candidates
            WHERE status IN (0, 1)
              AND (
                    (left_master_profile_id = :leftId AND right_master_profile_id = :rightId)
                 OR (left_master_profile_id = :rightId AND right_master_profile_id = :leftId)
              )
            """)
    Mono<Boolean> existsPendingOrMergedBetween(@Param("leftId") Long leftId, @Param("rightId") Long rightId);

    @Query("""
            SELECT * FROM profile_match_candidates
            WHERE (
                    (left_master_profile_id = :leftId AND right_master_profile_id = :rightId)
                 OR (left_master_profile_id = :rightId AND right_master_profile_id = :leftId)
              )
            ORDER BY created DESC
            """)
    Flux<ProfileMatchCandidate> findBetween(@Param("leftId") Long leftId, @Param("rightId") Long rightId);

    @Query("SELECT * FROM profile_match_candidates WHERE left_master_profile_id = :leftId " +
            "AND right_master_profile_id = :rightId ORDER BY created DESC LIMIT 1")
    Mono<ProfileMatchCandidate> findTopByLeftMasterProfileIdAndRightMasterProfileIdOrderByCreatedDesc(
            @Param("leftId") Long leftId, @Param("rightId") Long rightId);

    @Query("SELECT * FROM profile_match_candidates WHERE right_master_profile_id = :rightId " +
            "AND left_master_profile_id = :leftId ORDER BY created DESC LIMIT 1")
    Mono<ProfileMatchCandidate> findTopByRightMasterProfileIdAndLeftMasterProfileIdOrderByCreatedDesc(
            @Param("rightId") Long rightId, @Param("leftId") Long leftId);

    Flux<ProfileMatchCandidate> findByStatusOrderByMatchScoreDescCreatedDesc(Short status);

    Flux<ProfileMatchCandidate> findByLeftMasterProfileIdOrRightMasterProfileId(
            Long leftMasterProfileId, Long rightMasterProfileId);

    Flux<ProfileMatchCandidate> findByStatus(Short status);
}
