package vn.vnpost.cdp.profile.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.profile.entity.ProfileUnomiSyncLog;
import vn.vnpost.cdp.profile.repository.MasterProfileRepository;
import vn.vnpost.cdp.profile.repository.ProfileUnomiSyncLogRepository;
import vn.vnpost.cdp.profile.service.match.ProfileMatchCandidateService;
import vn.vnpost.cdp.security.SecurityUtils;
import vn.vnpost.cdp.unomi.service.UnomiService;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileMergedEventListener {

    private final MasterProfileRepository masterProfileRepository;
    private final ProfileUnomiSyncLogRepository unomiSyncLogRepository;
    private final UnomiService unomiService;
    private final ObjectMapper objectMapper;
    private final ProfileMatchCandidateService matchCandidateService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProfileMergedEvent(ProfileMergedEvent event) {
        MasterProfile profile = event.getProfile();
        String syncType = event.getSyncType();
        log.info("ProfileMergedEventListener - Handling async tasks for profileCode={}, syncType={}", profile.getProfileCode(), syncType);

        // 1. Sync to Unomi
        syncToUnomi(profile, syncType);

        // 2. Trigger match candidate detection
        try {
            matchCandidateService.detectAndCreateCandidatesForProfile(profile.getId());
        } catch (Exception ex) {
            log.warn("ProfileMergedEventListener - match candidate detection failed for profile {}: {}",
                    profile.getId(), ex.getMessage());
        }
    }

    private void syncToUnomi(MasterProfile profile, String syncType) {
        ProfileUnomiSyncLog syncLog = new ProfileUnomiSyncLog();
        syncLog.setMasterProfileId(profile.getId());
        syncLog.setProfileCode(profile.getProfileCode());
        syncLog.setSyncType(syncType);
        // Fallback to "system" for async execution
        syncLog.setCreatedBy(SecurityUtils.getCurrentUsername().orElse("system"));

        try {
            Object result = unomiService.syncProfileToUnomi(profile).block();
            syncLog.setStatus((short) 1); // SUCCESS
            syncLog.setResponsePayload(result != null ? objectMapper.convertValue(result, Map.class) : null);
            syncLog.setSyncedAt(LocalDateTime.now());
            profile.setSyncedToUnomiAt(LocalDateTime.now());
            masterProfileRepository.save(profile);
            log.info("ProfileMergedEventListener - Unomi sync SUCCESS: profileCode={}", profile.getProfileCode());
        } catch (Exception ex) {
            syncLog.setStatus((short) 2); // FAILED
            syncLog.setErrorMessage(ex.getMessage());
            syncLog.setSyncedAt(LocalDateTime.now());
            log.error("ProfileMergedEventListener - Unomi sync FAILED: profileCode={}",
                    profile.getProfileCode(), ex);
        }
        unomiSyncLogRepository.save(syncLog);
    }
}
