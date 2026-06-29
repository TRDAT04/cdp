package vn.vnpost.cdp.profile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.profile.dto.ProfileAttributeValueCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileAttributeValueResponse;
import vn.vnpost.cdp.profile.dto.ProfileAttributeValueUpdateRequest;
import vn.vnpost.cdp.profile.entity.ProfileAttributeValue;
import vn.vnpost.cdp.profile.repository.ProfileAttributeValueRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProfileAttributeValueServiceImpl implements ProfileAttributeValueService {

    private final ProfileAttributeValueRepository repository;

    public ProfileAttributeValueServiceImpl(ProfileAttributeValueRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ProfileAttributeValueResponse create(ProfileAttributeValueCreateRequest request) {
        log.info("Creating ProfileAttributeValue for masterProfileId={}, propertyName={}", request.getMasterProfileId(), request.getPropertyName());
        ProfileAttributeValue entity = new ProfileAttributeValue();
        entity.setMasterProfileId(request.getMasterProfileId());
        entity.setSourceRecordId(request.getSourceRecordId());
        entity.setSourceSystem(request.getSourceSystem());
        entity.setPropertyName(request.getPropertyName());
        entity.setPropertyValue(request.getPropertyValue());
        entity.setNormalizedValue(request.getNormalizedValue());
        entity.setConfidenceScore(request.getConfidenceScore());
        entity.setIsSelected(request.getIsSelected());
        entity.setReceivedAt(request.getReceivedAt() != null ? request.getReceivedAt() : LocalDateTime.now());
        ProfileAttributeValue saved = repository.save(entity);
        log.info("Created ProfileAttributeValue id={}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProfileAttributeValueResponse update(Long id, ProfileAttributeValueUpdateRequest request) {
        log.info("Updating ProfileAttributeValue id={}", id);
        ProfileAttributeValue entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("ATTRIBUTE_VALUE_NOT_FOUND", "Profile attribute value not found with id: " + id));
        if (request.getPropertyValue() != null) {
            entity.setPropertyValue(request.getPropertyValue());
        }
        if (request.getNormalizedValue() != null) {
            entity.setNormalizedValue(request.getNormalizedValue());
        }
        if (request.getConfidenceScore() != null) {
            entity.setConfidenceScore(request.getConfidenceScore());
        }
        if (request.getIsSelected() != null) {
            entity.setIsSelected(request.getIsSelected());
        }
        ProfileAttributeValue saved = repository.save(entity);
        log.info("Updated ProfileAttributeValue id={}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public ProfileAttributeValueResponse getById(Long id) {
        log.info("Getting ProfileAttributeValue id={}", id);
        ProfileAttributeValue entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("ATTRIBUTE_VALUE_NOT_FOUND", "Profile attribute value not found with id: " + id));
        return toResponse(entity);
    }

    @Override
    public List<ProfileAttributeValueResponse> listByMasterProfileId(Long masterProfileId) {
        log.info("Listing ProfileAttributeValues for masterProfileId={}", masterProfileId);
        return repository.findByMasterProfileId(masterProfileId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfileAttributeValueResponse> listByProperty(Long masterProfileId, String propertyName) {
        log.info("Listing ProfileAttributeValues for masterProfileId={}, propertyName={}", masterProfileId, propertyName);
        return repository.findByMasterProfileIdAndPropertyName(masterProfileId, propertyName)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProfileAttributeValueResponse selectValue(Long id) {
        log.info("Selecting ProfileAttributeValue id={}", id);
        ProfileAttributeValue entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("ATTRIBUTE_VALUE_NOT_FOUND", "Profile attribute value not found with id: " + id));
        entity.setIsSelected(true);
        ProfileAttributeValue saved = repository.save(entity);
        log.info("Selected ProfileAttributeValue id={}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProfileAttributeValueResponse unselectValue(Long id) {
        log.info("Unselecting ProfileAttributeValue id={}", id);
        ProfileAttributeValue entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("ATTRIBUTE_VALUE_NOT_FOUND", "Profile attribute value not found with id: " + id));
        entity.setIsSelected(false);
        ProfileAttributeValue saved = repository.save(entity);
        log.info("Unselected ProfileAttributeValue id={}", saved.getId());
        return toResponse(saved);
    }

    private ProfileAttributeValueResponse toResponse(ProfileAttributeValue entity) {
        return ProfileAttributeValueResponse.builder()
                .id(entity.getId())
                .masterProfileId(entity.getMasterProfileId())
                .sourceRecordId(entity.getSourceRecordId())
                .sourceSystem(entity.getSourceSystem())
                .propertyName(entity.getPropertyName())
                .propertyValue(entity.getPropertyValue())
                .normalizedValue(entity.getNormalizedValue())
                .confidenceScore(entity.getConfidenceScore())
                .isSelected(entity.getIsSelected())
                .receivedAt(entity.getReceivedAt())
                .createdBy(entity.getCreatedBy())
                .created(entity.getCreated())
                .modified(entity.getModified())
                .modifiedBy(entity.getModifiedBy())
                .build();
    }
}
