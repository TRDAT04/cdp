package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileSourceRecord;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileSourceRecordRepository extends JpaRepository<ProfileSourceRecord, Long> {

    /**
     * Chuyển các bản ghi nguồn từ profile bị merge (source) sang profile còn lại (target).
     * Giữ lại dấu vết "hồ sơ này được tạo/enrich từ nguồn nào, lúc nào" cho tab So sánh đa nguồn
     * sau khi merge — nếu không re-point, các bản ghi này thành mồ côi và tab mất dữ liệu.
     */
    @Modifying(flushAutomatically = true)
    @Query("update ProfileSourceRecord r set r.masterProfileId = :targetId where r.masterProfileId = :sourceId")
    int reassignMasterProfile(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);

    List<ProfileSourceRecord> findByMasterProfileId(Long masterProfileId);
    List<ProfileSourceRecord> findByMasterProfileIdOrderByReceivedAtDesc(Long masterProfileId);
    List<ProfileSourceRecord> findBySourceSystem(String sourceSystem);
    List<ProfileSourceRecord> findByMergeStatus(Short mergeStatus);

    Optional<ProfileSourceRecord> findFirstBySourceSystemAndSourceCustomerIdOrderByReceivedAtDesc(
            String sourceSystem,
            String sourceCustomerId
    );
}
