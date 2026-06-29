package vn.vnpost.cdp.profile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.profile.dto.ProfileMergeConflictCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeConflictResolveRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeConflictResponse;
import vn.vnpost.cdp.profile.entity.ProfileMergeConflict;
import vn.vnpost.cdp.profile.repository.ProfileMergeConflictRepository;
import vn.vnpost.cdp.profile.service.ProfileMergeConflictService;
import vn.vnpost.cdp.security.SecurityUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProfileMergeConflictServiceImpl implements ProfileMergeConflictService {

    private final ProfileMergeConflictRepository repository;

    public ProfileMergeConflictServiceImpl(ProfileMergeConflictRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ProfileMergeConflictResponse create(ProfileMergeConflictCreateRequest request) {
        log.info("Creating profile merge conflict for masterProfileId={}", request.getMasterProfileId());
        ProfileMergeConflict entity = new ProfileMergeConflict();
        entity.setMasterProfileId(request.getMasterProfileId());
        entity.setSourceRecordId(request.getSourceRecordId());
        entity.setPropertyName(request.getPropertyName());
        entity.setCurrentValue(request.getCurrentValue());
        entity.setIncomingValue(request.getIncomingValue());
        entity.setCurrentSource(request.getCurrentSource());
        entity.setIncomingSource(request.getIncomingSource());
        entity.setConflictReason(request.getConflictReason());
        entity.setResolutionStatus((short) 0);
        entity = repository.save(entity);
        log.info("Created profile merge conflict id={}", entity.getId());
        return toResponse(entity);
    }

    @Override
    public ProfileMergeConflictResponse getById(Long id) {
        log.info("Getting profile merge conflict id={}", id);
        ProfileMergeConflict entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("CONFLICT_NOT_FOUND", "Profile merge conflict not found with id: " + id));
        return toResponse(entity);
    }

    @Override
    public List<ProfileMergeConflictResponse> listByMasterProfileId(Long masterProfileId) {
        log.info("Listing profile merge conflicts by masterProfileId={}", masterProfileId);
        return repository.findByMasterProfileId(masterProfileId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfileMergeConflictResponse> listByResolutionStatus(Short resolutionStatus) {
        log.info("Listing profile merge conflicts by resolutionStatus={}", resolutionStatus);
        return repository.findByResolutionStatus(resolutionStatus).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProfileMergeConflictResponse resolve(Long id, ProfileMergeConflictResolveRequest request) {
        log.info("Resolving profile merge conflict id={}", id);
        ProfileMergeConflict entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("CONFLICT_NOT_FOUND", "Profile merge conflict not found with id: " + id));
        if (entity.getResolutionStatus() != 0) {
            throw new BusinessException("CONFLICT_ALREADY_RESOLVED", "Conflict already resolved");
        }
        entity.setResolutionStatus((short) 1);
        entity.setResolvedValue(request.getResolvedValue());
        entity.setResolvedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        entity.setResolvedAt(LocalDateTime.now());
        entity = repository.save(entity);
        log.info("Resolved profile merge conflict id={}", entity.getId());
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ProfileMergeConflictResponse reject(Long id) {
        log.info("Rejecting profile merge conflict id={}", id);
        ProfileMergeConflict entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("CONFLICT_NOT_FOUND", "Profile merge conflict not found with id: " + id));
        entity.setResolutionStatus((short) 2);
        entity.setResolvedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        entity.setResolvedAt(LocalDateTime.now());
        entity = repository.save(entity);
        log.info("Rejected profile merge conflict id={}", entity.getId());
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ProfileMergeConflictResponse ignore(Long id) {
        log.info("Ignoring profile merge conflict id={}", id);
        ProfileMergeConflict entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("CONFLICT_NOT_FOUND", "Profile merge conflict not found with id: " + id));
        entity.setResolutionStatus((short) 3);
        entity.setResolvedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        entity.setResolvedAt(LocalDateTime.now());
        entity = repository.save(entity);
        log.info("Ignored profile merge conflict id={}", entity.getId());
        return toResponse(entity);
    }

    private ProfileMergeConflictResponse toResponse(ProfileMergeConflict entity) {
        return ProfileMergeConflictResponse.builder()
                .id(entity.getId())
                .masterProfileId(entity.getMasterProfileId())
                .sourceRecordId(entity.getSourceRecordId())
                .propertyName(entity.getPropertyName())
                .currentValue(entity.getCurrentValue())
                .incomingValue(entity.getIncomingValue())
                .currentSource(entity.getCurrentSource())
                .incomingSource(entity.getIncomingSource())
                .conflictReason(entity.getConflictReason())
                .resolutionStatus(entity.getResolutionStatus())
                .resolvedValue(entity.getResolvedValue())
                .resolvedBy(entity.getResolvedBy())
                .resolvedAt(entity.getResolvedAt())
                .createdBy(entity.getCreatedBy())
                .created(entity.getCreated())
                .modified(entity.getModified())
                .modifiedBy(entity.getModifiedBy())
                .build();
    }
}
