package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileIdentityLink;

import java.util.List;

@Repository
public interface ProfileIdentityLinkRepository extends JpaRepository<ProfileIdentityLink, Long> {
    List<ProfileIdentityLink> findByMasterProfileId(Long masterProfileId);
    List<ProfileIdentityLink> findByMasterProfileIdAndStatus(Long masterProfileId, Short status);

    // Trả List, không Optional: profile_identity_links KHÔNG có unique constraint trên
    // (source_system, source_customer_id). Sau mỗi lần admin merge, copyIdentityLinks() để lại
    // link cũ status=3 (MERGED) trên hồ sơ nguồn VÀ tạo link mới status=1 trên hồ sơ đích — tức là
    // 2 dòng cùng (source_system, source_customer_id). Optional sẽ ném
    // IncorrectResultSizeDataAccessException ở lần ingest tiếp theo của chính source customer đó.
    List<ProfileIdentityLink> findBySourceSystemAndSourceCustomerId(String sourceSystem, String sourceCustomerId);
    List<ProfileIdentityLink> findBySourceSystemAndSourceCustomerIdAndStatus(String sourceSystem, String sourceCustomerId, Short status);

    List<ProfileIdentityLink> findByIdentityTypeAndIdentityValue(String identityType, String identityValue);
    List<ProfileIdentityLink> findByMasterProfileIdAndIdentityTypeAndIdentityValue(
            Long masterProfileId, String identityType, String identityValue);
}
