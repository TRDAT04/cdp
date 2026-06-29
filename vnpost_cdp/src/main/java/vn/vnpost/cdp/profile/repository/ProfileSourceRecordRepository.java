package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.ProfileSourceRecord;

import java.util.List;

@Repository
public interface ProfileSourceRecordRepository extends JpaRepository<ProfileSourceRecord, Long> {
    List<ProfileSourceRecord> findByMasterProfileId(Long masterProfileId);
    List<ProfileSourceRecord> findByMasterProfileIdOrderByReceivedAtDesc(Long masterProfileId);
    List<ProfileSourceRecord> findBySourceSystem(String sourceSystem);
    List<ProfileSourceRecord> findByMergeStatus(Short mergeStatus);
}
