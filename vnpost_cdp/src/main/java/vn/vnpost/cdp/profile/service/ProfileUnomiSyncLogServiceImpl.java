package vn.vnpost.cdp.profile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.profile.dto.ProfileUnomiSyncLogCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileUnomiSyncLogResponse;
import vn.vnpost.cdp.profile.entity.ProfileUnomiSyncLog;
import vn.vnpost.cdp.profile.repository.ProfileUnomiSyncLogRepository;
import vn.vnpost.cdp.profile.service.ProfileUnomiSyncLogService;
import vn.vnpost.cdp.security.SecurityUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProfileUnomiSyncLogServiceImpl implements ProfileUnomiSyncLogService {

    private final ProfileUnomiSyncLogRepository repository;

    public ProfileUnomiSyncLogServiceImpl(ProfileUnomiSyncLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ProfileUnomiSyncLogResponse create(ProfileUnomiSyncLogCreateRequest request) {
        log.info("Creating profile unomi sync log for masterProfileId={}, profileCode={}",
                request.getMasterProfileId(), request.getProfileCode());
        ProfileUnomiSyncLog entity = new ProfileUnomiSyncLog();
        entity.setMasterProfileId(request.getMasterProfileId());
        entity.setProfileCode(request.getProfileCode());
        entity.setSyncType(request.getSyncType());
        entity.setRequestPayload(request.getRequestPayload());
        entity.setResponsePayload(request.getResponsePayload());
        entity.setStatus((short) 0);
        entity.setSyncedAt(LocalDateTime.now());
        entity.setCreatedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        entity = repository.save(entity);
        log.info("Created profile unomi sync log id={}", entity.getId());
        return toResponse(entity);
    }

    @Override
    public ProfileUnomiSyncLogResponse getById(Long id) {
        log.info("Getting profile unomi sync log id={}", id);
        ProfileUnomiSyncLog entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("SYNC_LOG_NOT_FOUND", "Profile unomi sync log not found with id: " + id));
        return toResponse(entity);
    }

    @Override
    public List<ProfileUnomiSyncLogResponse> listByMasterProfileId(Long masterProfileId) {
        log.info("Listing profile unomi sync logs by masterProfileId={}", masterProfileId);
        return repository.findByMasterProfileIdOrderBySyncedAtDesc(masterProfileId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfileUnomiSyncLogResponse> listByProfileCode(String profileCode) {
        log.info("Listing profile unomi sync logs by profileCode={}", profileCode);
        return repository.findByProfileCodeOrderBySyncedAtDesc(profileCode).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfileUnomiSyncLogResponse> listByStatus(Short status) {
        log.info("Listing profile unomi sync logs by status={}", status);
        return repository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProfileUnomiSyncLogResponse markSuccess(Long id, Map<String, Object> responsePayload) {
        log.info("Marking profile unomi sync log id={} as SUCCESS", id);
        ProfileUnomiSyncLog entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("SYNC_LOG_NOT_FOUND", "Profile unomi sync log not found with id: " + id));
        entity.setStatus((short) 1);
        entity.setResponsePayload(responsePayload);
        entity.setSyncedAt(LocalDateTime.now());
        entity = repository.save(entity);
        log.info("Marked profile unomi sync log id={} as SUCCESS", entity.getId());
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ProfileUnomiSyncLogResponse markFailed(Long id, String errorMessage) {
        log.info("Marking profile unomi sync log id={} as FAILED", id);
        ProfileUnomiSyncLog entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("SYNC_LOG_NOT_FOUND", "Profile unomi sync log not found with id: " + id));
        entity.setStatus((short) 2);
        entity.setErrorMessage(errorMessage);
        entity.setSyncedAt(LocalDateTime.now());
        entity = repository.save(entity);
        log.info("Marked profile unomi sync log id={} as FAILED", entity.getId());
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ProfileUnomiSyncLogResponse markRetrying(Long id) {
        log.info("Marking profile unomi sync log id={} as RETRYING", id);
        ProfileUnomiSyncLog entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("SYNC_LOG_NOT_FOUND", "Profile unomi sync log not found with id: " + id));
        entity.setStatus((short) 3);
        entity.setSyncedAt(LocalDateTime.now());
        entity = repository.save(entity);
        log.info("Marked profile unomi sync log id={} as RETRYING", entity.getId());
        return toResponse(entity);
    }

    private ProfileUnomiSyncLogResponse toResponse(ProfileUnomiSyncLog entity) {
        return ProfileUnomiSyncLogResponse.builder()
                .id(entity.getId())
                .masterProfileId(entity.getMasterProfileId())
                .profileCode(entity.getProfileCode())
                .syncType(entity.getSyncType())
                .requestPayload(entity.getRequestPayload())
                .responsePayload(entity.getResponsePayload())
                .status(entity.getStatus())
                .errorMessage(entity.getErrorMessage())
                .syncedAt(entity.getSyncedAt())
                .createdBy(entity.getCreatedBy())
                .build();
    }
}
