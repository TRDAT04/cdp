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

    /**
     * Các candidate ở một trạng thái cụ thể còn tham chiếu tới một profile (ở vế left hoặc right).
     * Dùng sau khi merge để vô hiệu hoá những candidate PENDING vẫn trỏ tới profile đã bị merge —
     * nếu để nguyên, admin có thể bấm merge trên một hồ sơ đã "chết".
     */
    @Query("""
        select c
        from ProfileMatchCandidate c
        where c.status = :status
          and (c.leftMasterProfileId = :profileId or c.rightMasterProfileId = :profileId)
    """)
    List<ProfileMatchCandidate> findByProfileIdAndStatus(@Param("profileId") Long profileId,
                                                         @Param("status") Short status);

    // =====================================================================
    // GROUPED PENDING — màn "Đối soát định danh"
    // =====================================================================

    /**
     * Gom candidate PENDING theo từng hồ sơ gốc. Một candidate là một CẶP, nên phải "unpivot"
     * vế left/right thành hai dòng ({@code UNION ALL}) trước khi GROUP BY — JPQL không có UNION
     * nên phải dùng native query.
     *
     * <p>Aggregate chạy ở DB thay vì load hết candidate rồi group bằng Java stream: chi phí không
     * phụ thuộc tổng số candidate, và {@code LIMIT/OFFSET} phân trang được ngay trên tập đã gom.
     *
     * <p>Trả về mỗi dòng: {@code [id, profile_code, full_name, customer_type, phone, tax_code,
     * identity_no, pending_count, max_score, min_score]}.
     *
     * @param keyword pattern ILIKE đã bọc sẵn {@code %...%}, hoặc null để bỏ qua filter
     */
    @Query(value = """
            WITH pending AS (
                SELECT c.id AS candidate_id, c.left_master_profile_id AS mp_id, c.match_score
                  FROM profile_match_candidates c
                 WHERE c.status = 0
                UNION ALL
                SELECT c.id, c.right_master_profile_id, c.match_score
                  FROM profile_match_candidates c
                 WHERE c.status = 0
            )
            SELECT mp.id,
                   mp.profile_code,
                   mp.full_name,
                   mp.customer_type,
                   mp.phone,
                   mp.tax_code,
                   mp.identity_no,
                   COUNT(DISTINCT p.candidate_id) AS pending_count,
                   MAX(p.match_score)             AS max_score,
                   MIN(p.match_score)             AS min_score
              FROM pending p
              JOIN master_profiles mp ON mp.id = p.mp_id
             WHERE mp.status NOT IN (3, 5)
               AND (CAST(:keyword AS text) IS NULL
                    OR mp.full_name    ILIKE CAST(:keyword AS text)
                    OR mp.profile_code ILIKE CAST(:keyword AS text)
                    OR mp.phone        ILIKE CAST(:keyword AS text)
                    OR mp.tax_code     ILIKE CAST(:keyword AS text))
             GROUP BY mp.id, mp.profile_code, mp.full_name, mp.customer_type,
                      mp.phone, mp.tax_code, mp.identity_no
             ORDER BY MAX(p.match_score) DESC, COUNT(DISTINCT p.candidate_id) DESC, mp.id
             LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> findPendingGroups(@Param("keyword") String keyword,
                                     @Param("limit") int limit,
                                     @Param("offset") long offset);

    /**
     * Tổng số hồ sơ gốc có candidate PENDING (cùng điều kiện với {@link #findPendingGroups}),
     * dùng cho {@code totalRecord} của response.
     */
    @Query(value = """
            SELECT COUNT(*) FROM (
                WITH pending AS (
                    SELECT c.id AS candidate_id, c.left_master_profile_id AS mp_id
                      FROM profile_match_candidates c
                     WHERE c.status = 0
                    UNION ALL
                    SELECT c.id, c.right_master_profile_id
                      FROM profile_match_candidates c
                     WHERE c.status = 0
                )
                SELECT mp.id
                  FROM pending p
                  JOIN master_profiles mp ON mp.id = p.mp_id
                 WHERE mp.status NOT IN (3, 5)
                   AND (CAST(:keyword AS text) IS NULL
                        OR mp.full_name    ILIKE CAST(:keyword AS text)
                        OR mp.profile_code ILIKE CAST(:keyword AS text)
                        OR mp.phone        ILIKE CAST(:keyword AS text)
                        OR mp.tax_code     ILIKE CAST(:keyword AS text))
                 GROUP BY mp.id
            ) grouped
            """, nativeQuery = true)
    long countPendingGroups(@Param("keyword") String keyword);

    /**
     * Các reasonType khớp (distinct) của từng hồ sơ, chỉ chạy trên danh sách id của TRANG hiện tại
     * nên tập đầu vào luôn nhỏ. Loại các reason dạng {@code *_CONFLICT} — "khoá khớp" chỉ gồm
     * trường thực sự trùng. Không lọc theo {@code '%_MATCH'} vì {@code NAME_SIMILAR}
     * ("Tên gần đúng") cũng là một khoá khớp nhưng không kết thúc bằng {@code _MATCH}.
     *
     * <p>Trả về mỗi dòng: {@code [mp_id, reason_type]}.
     */
    @Query(value = """
            WITH pending AS (
                SELECT c.id AS candidate_id, c.left_master_profile_id AS mp_id
                  FROM profile_match_candidates c
                 WHERE c.status = 0
                UNION ALL
                SELECT c.id, c.right_master_profile_id
                  FROM profile_match_candidates c
                 WHERE c.status = 0
            )
            SELECT p.mp_id, r.reason_type
              FROM pending p
              JOIN profile_match_reasons r ON r.match_candidate_id = p.candidate_id
             WHERE p.mp_id IN (:profileIds)
               AND r.reason_type NOT LIKE '%\\_CONFLICT'
             GROUP BY p.mp_id, r.reason_type
             ORDER BY p.mp_id, r.reason_type
            """, nativeQuery = true)
    List<Object[]> findMatchedReasonTypes(@Param("profileIds") List<Long> profileIds);
}
