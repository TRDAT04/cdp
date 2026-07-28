package vn.vnpost.example.profile.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import vn.vnpost.example.profile.entity.MasterProfile;
import vn.vnpost.example.profile.entity.ProfileUnomiSyncLog;
import vn.vnpost.example.profile.repository.MasterProfileRepository;
import vn.vnpost.example.profile.repository.ProfileUnomiSyncLogRepository;
import vn.vnpost.example.profile.service.match.ProfileMatchCandidateService;
import vn.vnpost.example.security.SecurityUtils;
import vn.vnpost.example.unomi.service.UnomiService;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Thay thế {@code ProfileMergedEvent} + {@code ProfileMergedEventListener} (
 * {@code @Async} + {@code @TransactionalEventListener(AFTER_COMMIT)}) của bản gốc — reactive
 * Spring không có cơ chế tương đương trực tiếp cho "after commit, async, fire-and-forget".
 *
 * <p>Thay vào đó, đây là một service reactive được gọi TRỰC TIẾP (không qua ApplicationEvent)
 * ngay sau khi Mono ghi dữ liệu chính (createNewProfile/autoMerge...) hoàn tất — tức sau khi
 * transaction chính đã commit. Caller ({@code ProfileIngestionServiceImpl}) subscribe Mono này
 * một cách tách rời (không block luồng response chính, lỗi ở đây không làm luồng ingestion
 * chính thất bại), mô phỏng lại đúng ngữ nghĩa "async, sau commit, không ảnh hưởng nghiệp vụ
 * chính nếu thất bại" của bản gốc.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileMergePostProcessingService {

    private final MasterProfileRepository masterProfileRepository;
    private final ProfileUnomiSyncLogRepository unomiSyncLogRepository;
    private final UnomiService unomiService;
    private final ObjectMapper objectMapper;
    private final ProfileMatchCandidateService matchCandidateService;

    public Mono<Void> handle(MasterProfile profile, String syncType) {
        log.info("ProfileMergePostProcessingService - Handling async tasks for profileCode={}, syncType={}",
                profile.getProfileCode(), syncType);

        return syncToUnomi(profile, syncType)
                .then(matchCandidateService.detectAndCreateCandidatesForProfile(profile.getId())
                        .onErrorResume(ex -> {
                            log.warn("ProfileMergePostProcessingService - match candidate detection failed " +
                                    "for profile {}: {}", profile.getId(), ex.getMessage());
                            return Mono.empty();
                        }));
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> syncToUnomi(MasterProfile profile, String syncType) {
        return SecurityUtils.getCurrentUsernameOrSystem()
                .flatMap(actor -> {
                    ProfileUnomiSyncLog syncLog = new ProfileUnomiSyncLog();
                    syncLog.setMasterProfileId(profile.getId());
                    syncLog.setProfileCode(profile.getProfileCode());
                    syncLog.setSyncType(syncType);
                    syncLog.setCreatedBy(actor);

                    return unomiService.syncProfileToUnomi(profile)
                            .flatMap(result -> {
                                syncLog.setStatus((short) 1); // SUCCESS
                                syncLog.setResponsePayload(result != null
                                        ? objectMapper.convertValue(result, Map.class) : null);
                                syncLog.setSyncedAt(LocalDateTime.now());
                                profile.setSyncedToUnomiAt(LocalDateTime.now());
                                log.info("ProfileMergePostProcessingService - Unomi sync SUCCESS: profileCode={}",
                                        profile.getProfileCode());
                                return masterProfileRepository.save(profile);
                            })
                            .onErrorResume(ex -> {
                                syncLog.setStatus((short) 2); // FAILED
                                syncLog.setErrorMessage(ex.getMessage());
                                syncLog.setSyncedAt(LocalDateTime.now());
                                log.error("ProfileMergePostProcessingService - Unomi sync FAILED: profileCode={}",
                                        profile.getProfileCode(), ex);
                                return Mono.empty();
                            })
                            .then(Mono.defer(() -> unomiSyncLogRepository.save(syncLog)))
                            .then();
                });
    }
}
