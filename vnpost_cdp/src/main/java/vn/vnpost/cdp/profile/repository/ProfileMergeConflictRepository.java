package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileMergeConflict;

import java.util.List;

@Repository
public interface ProfileMergeConflictRepository extends JpaRepository<ProfileMergeConflict, Long> {

    /**
     * Chuyển các conflict từ profile bị merge (source) sang profile còn lại (target).
     *
     * <p>Xung đột field (VD: CMS gửi phone khác CRM) KHÔNG được merge giải quyết — nó vẫn còn
     * nguyên và giờ thuộc về hồ sơ target. Nếu để nguyên ở source, conflict OPEN thành mồ côi:
     * không ai còn thấy để xử lý, nhưng vẫn bị đếm trong thống kê "conflict đang mở".
     */
    @Modifying(flushAutomatically = true)
    @Query("update ProfileMergeConflict c set c.masterProfileId = :targetId where c.masterProfileId = :sourceId")
    int reassignMasterProfile(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);

    List<ProfileMergeConflict> findByMasterProfileId(Long masterProfileId);
    List<ProfileMergeConflict> findByMasterProfileIdAndResolutionStatus(Long masterProfileId, Short resolutionStatus);
    boolean existsByMasterProfileIdAndResolutionStatus(Long masterProfileId, Short resolutionStatus);

    List<ProfileMergeConflict> findByResolutionStatus(Short resolutionStatus);
}
