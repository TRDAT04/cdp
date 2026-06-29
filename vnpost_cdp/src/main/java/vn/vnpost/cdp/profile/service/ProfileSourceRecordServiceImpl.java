package vn.vnpost.cdp.profile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.profile.dto.ProfileSourceRecordCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileSourceRecordResponse;
import vn.vnpost.cdp.profile.dto.ProfileSourceRecordUpdateRequest;
import vn.vnpost.cdp.profile.entity.ProfileSourceRecord;
import vn.vnpost.cdp.profile.repository.ProfileSourceRecordRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProfileSourceRecordServiceImpl implements ProfileSourceRecordService {

    private final ProfileSourceRecordRepository repository;

    public ProfileSourceRecordServiceImpl(ProfileSourceRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ProfileSourceRecordResponse create(ProfileSourceRecordCreateRequest request) {
        log.info("Creating profile source record for sourceSystem: {}, sourceCustomerId: {}",
                request.getSourceSystem(), request.getSourceCustomerId());
        ProfileSourceRecord entity = new ProfileSourceRecord();
        entity.setSourceSystem(request.getSourceSystem());
        entity.setSourceCustomerId(request.getSourceCustomerId());
        entity.setSourceEventId(request.getSourceEventId());
        entity.setMasterProfileId(request.getMasterProfileId());
        entity.setIdentityKey(request.getIdentityKey());
        entity.setRawPayload(request.getRawPayload());
        entity.setNormalizedPayload(request.getNormalizedPayload());
        entity.setReceivedAt(request.getReceivedAt() != null ? request.getReceivedAt() : LocalDateTime.now());
        entity.setMergeStatus((short) 0);
        ProfileSourceRecord saved = repository.save(entity);
        log.info("Created profile source record with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProfileSourceRecordResponse update(Long id, ProfileSourceRecordUpdateRequest request) {
        log.info("Updating profile source record with id: {}", id);
        ProfileSourceRecord entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("SOURCE_RECORD_NOT_FOUND", "Source record not found with id: " + id));
        if (request.getMasterProfileId() != null) {
            entity.setMasterProfileId(request.getMasterProfileId());
        }
        if (request.getNormalizedPayload() != null) {
            entity.setNormalizedPayload(request.getNormalizedPayload());
        }
        if (request.getMergeStatus() != null) {
            entity.setMergeStatus(request.getMergeStatus());
        }
        if (request.getErrorMessage() != null) {
            entity.setErrorMessage(request.getErrorMessage());
        }
        if (request.getProcessedAt() != null) {
            entity.setProcessedAt(request.getProcessedAt());
        }
        ProfileSourceRecord saved = repository.save(entity);
        log.info("Updated profile source record with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public ProfileSourceRecordResponse getById(Long id) {
        ProfileSourceRecord entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("SOURCE_RECORD_NOT_FOUND", "Source record not found with id: " + id));
        return toResponse(entity);
    }

    @Override
    public List<ProfileSourceRecordResponse> listByMasterProfileId(Long masterProfileId) {
        return repository.findByMasterProfileId(masterProfileId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfileSourceRecordResponse> listBySourceSystem(String sourceSystem) {
        return repository.findBySourceSystem(sourceSystem).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfileSourceRecordResponse> listByMergeStatus(Short mergeStatus) {
        return repository.findByMergeStatus(mergeStatus).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProfileSourceRecordResponse markProcessed(Long id) {
        log.info("Marking profile source record id: {} as processed", id);
        ProfileSourceRecord entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("SOURCE_RECORD_NOT_FOUND", "Source record not found with id: " + id));
        entity.setMergeStatus((short) 1);
        entity.setProcessedAt(LocalDateTime.now());
        return toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public ProfileSourceRecordResponse markError(Long id, String errorMessage) {
        log.info("Marking profile source record id: {} as error", id);
        ProfileSourceRecord entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("SOURCE_RECORD_NOT_FOUND", "Source record not found with id: " + id));
        entity.setMergeStatus((short) 5);
        entity.setErrorMessage(errorMessage);
        return toResponse(repository.save(entity));
    }

    private ProfileSourceRecordResponse toResponse(ProfileSourceRecord entity) {
        return ProfileSourceRecordResponse.builder()
                .id(entity.getId())
                .sourceSystem(entity.getSourceSystem())
                .sourceCustomerId(entity.getSourceCustomerId())
                .sourceEventId(entity.getSourceEventId())
                .masterProfileId(entity.getMasterProfileId())
                .identityKey(entity.getIdentityKey())
                .rawPayload(entity.getRawPayload())
                .normalizedPayload(entity.getNormalizedPayload())
                .receivedAt(entity.getReceivedAt())
                .processedAt(entity.getProcessedAt())
                .mergeStatus(entity.getMergeStatus())
                .errorMessage(entity.getErrorMessage())
                .createdBy(entity.getCreatedBy())
                .created(entity.getCreated())
                .modified(entity.getModified())
                .modifiedBy(entity.getModifiedBy())
                .build();
    }
}
