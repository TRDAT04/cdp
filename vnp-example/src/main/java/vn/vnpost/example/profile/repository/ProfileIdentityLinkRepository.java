package vn.vnpost.example.profile.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import vn.vnpost.example.profile.entity.ProfileIdentityLink;

@Repository
public interface ProfileIdentityLinkRepository extends ReactiveCrudRepository<ProfileIdentityLink, Long> {

    Flux<ProfileIdentityLink> findByMasterProfileId(Long masterProfileId);

    Flux<ProfileIdentityLink> findByMasterProfileIdAndStatus(Long masterProfileId, Short status);

    Flux<ProfileIdentityLink> findByIdentityTypeAndIdentityValue(String identityType, String identityValue);

    // Trả Flux, không Mono: profile_identity_links KHÔNG có unique constraint trên
    // (source_system, source_customer_id). Sau mỗi lần admin merge, copyIdentityLinks() để lại
    // link cũ status=3 (MERGED) trên hồ sơ nguồn VÀ tạo link mới status=1 trên hồ sơ đích — tức là
    // 2 dòng cùng (source_system, source_customer_id). Mono sẽ ném
    // IncorrectResultSizeDataAccessException ở lần ingest tiếp theo của chính source customer đó.
    Flux<ProfileIdentityLink> findBySourceSystemAndSourceCustomerId(String sourceSystem, String sourceCustomerId);

    Flux<ProfileIdentityLink> findBySourceSystemAndSourceCustomerIdAndStatus(String sourceSystem,
                                                                            String sourceCustomerId, Short status);

    Flux<ProfileIdentityLink> findByMasterProfileIdAndIdentityTypeAndIdentityValue(
            Long masterProfileId, String identityType, String identityValue);

    /**
     * Thay thế subquery JPA (root.id IN (SELECT masterProfileId FROM ProfileIdentityLink WHERE ...))
     * của {@code searchProfiles}: R2DBC không hỗ trợ subquery trong Criteria nên tách thành
     * query riêng, ghép điều kiện {@code id IN (:ids)} ở tầng service.
     */
    @Query("SELECT DISTINCT master_profile_id FROM profile_identity_links " +
            "WHERE source_system = :sourceSystem AND status = :status")
    Flux<Long> findDistinctMasterProfileIdBySourceSystemAndStatus(@Param("sourceSystem") String sourceSystem,
                                                                    @Param("status") Short status);
}
