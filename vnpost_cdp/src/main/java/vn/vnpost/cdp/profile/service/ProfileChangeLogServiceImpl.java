package vn.vnpost.cdp.profile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.profile.dto.ProfileChangeLogCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileChangeLogResponse;
import vn.vnpost.cdp.profile.entity.ProfileChangeLog;
import vn.vnpost.cdp.profile.repository.ProfileChangeLogRepository;
import vn.vnpost.cdp.security.SecurityUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProfileChangeLogServiceImpl implements ProfileChangeLogService {

    private final ProfileChangeLogRepository repository;

    public ProfileChangeLogServiceImpl(ProfileChangeLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ProfileChangeLogResponse create(ProfileChangeLogCreateRequest request) {
        log.info("Creating ProfileChangeLog for masterProfileId={}, eventType={}", request.getMasterProfileId(), request.getEventType());
        ProfileChangeLog entity = new ProfileChangeLog();
        entity.setMasterProfileId(request.getMasterProfileId());
        entity.setSourceRecordId(request.getSourceRecordId());
        entity.setSourceSystem(request.getSourceSystem());
        entity.setEventType(request.getEventType());
        entity.setPropertyName(request.getPropertyName());
        entity.setOldValue(request.getOldValue());
        entity.setNewValue(request.getNewValue());
        entity.setSelectedValue(request.getSelectedValue());
        entity.setOldSource(request.getOldSource());
        entity.setNewSource(request.getNewSource());
        entity.setMergeStrategy(request.getMergeStrategy());
        entity.setReason(request.getReason());
        entity.setChangedAt(LocalDateTime.now());
        entity.setChangedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        ProfileChangeLog saved = repository.save(entity);
        log.info("Created ProfileChangeLog id={}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public ProfileChangeLogResponse getById(Long id) {
        log.info("Getting ProfileChangeLog id={}", id);
        ProfileChangeLog entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("CHANGE_LOG_NOT_FOUND", "Profile change log not found with id: " + id));
        return toResponse(entity);
    }

    @Override
    public List<ProfileChangeLogResponse> listByMasterProfileId(Long masterProfileId) {
        log.info("Listing ProfileChangeLogs for masterProfileId={}", masterProfileId);
        return repository.findByMasterProfileIdOrderByChangedAtDesc(masterProfileId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfileChangeLogResponse> listByPropertyName(Long masterProfileId, String propertyName) {
        log.info("Listing ProfileChangeLogs for masterProfileId={}, propertyName={}", masterProfileId, propertyName);
        return repository.findByMasterProfileIdAndPropertyNameOrderByChangedAtDesc(masterProfileId, propertyName)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private ProfileChangeLogResponse toResponse(ProfileChangeLog entity) {
        return ProfileChangeLogResponse.builder()
                .id(entity.getId())
                .masterProfileId(entity.getMasterProfileId())
                .sourceRecordId(entity.getSourceRecordId())
                .sourceSystem(entity.getSourceSystem())
                .eventType(entity.getEventType())
                .propertyName(entity.getPropertyName())
                .oldValue(entity.getOldValue())
                .newValue(entity.getNewValue())
                .selectedValue(entity.getSelectedValue())
                .oldSource(entity.getOldSource())
                .newSource(entity.getNewSource())
                .mergeStrategy(entity.getMergeStrategy())
                .reason(entity.getReason())
                .changedBy(entity.getChangedBy())
                .changedAt(entity.getChangedAt())
                .build();
    }
}
