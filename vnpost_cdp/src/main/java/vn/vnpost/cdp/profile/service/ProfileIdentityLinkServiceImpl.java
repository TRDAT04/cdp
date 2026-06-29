package vn.vnpost.cdp.profile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.profile.dto.ProfileIdentityLinkCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileIdentityLinkResponse;
import vn.vnpost.cdp.profile.dto.ProfileIdentityLinkUpdateRequest;
import vn.vnpost.cdp.profile.entity.ProfileIdentityLink;
import vn.vnpost.cdp.profile.repository.ProfileIdentityLinkRepository;
import vn.vnpost.cdp.security.SecurityUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProfileIdentityLinkServiceImpl implements ProfileIdentityLinkService {

    private final ProfileIdentityLinkRepository repository;

    public ProfileIdentityLinkServiceImpl(ProfileIdentityLinkRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ProfileIdentityLinkResponse create(ProfileIdentityLinkCreateRequest request) {
        log.info("Creating profile identity link for masterProfileId: {}", request.getMasterProfileId());
        ProfileIdentityLink entity = new ProfileIdentityLink();
        entity.setMasterProfileId(request.getMasterProfileId());
        entity.setSourceSystem(request.getSourceSystem());
        entity.setSourceCustomerId(request.getSourceCustomerId());
        entity.setIdentityType(request.getIdentityType());
        entity.setIdentityValue(request.getIdentityValue());
        entity.setConfidenceScore(request.getConfidenceScore());
        entity.setIsPrimary(request.getIsPrimary());
        entity.setStatus((short) 1);
        entity.setLinkedAt(LocalDateTime.now());
        entity.setLinkedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        ProfileIdentityLink saved = repository.save(entity);
        log.info("Created profile identity link with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProfileIdentityLinkResponse update(Long id, ProfileIdentityLinkUpdateRequest request) {
        log.info("Updating profile identity link with id: {}", id);
        ProfileIdentityLink entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("IDENTITY_LINK_NOT_FOUND", "Identity link not found with id: " + id));
        if (request.getIdentityType() != null) {
            entity.setIdentityType(request.getIdentityType());
        }
        if (request.getIdentityValue() != null) {
            entity.setIdentityValue(request.getIdentityValue());
        }
        if (request.getConfidenceScore() != null) {
            entity.setConfidenceScore(request.getConfidenceScore());
        }
        if (request.getIsPrimary() != null) {
            entity.setIsPrimary(request.getIsPrimary());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        ProfileIdentityLink saved = repository.save(entity);
        log.info("Updated profile identity link with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public ProfileIdentityLinkResponse getById(Long id) {
        ProfileIdentityLink entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("IDENTITY_LINK_NOT_FOUND", "Identity link not found with id: " + id));
        return toResponse(entity);
    }

    @Override
    public List<ProfileIdentityLinkResponse> listByMasterProfileId(Long masterProfileId) {
        return repository.findByMasterProfileId(masterProfileId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfileIdentityLinkResponse> listByIdentity(String identityType, String identityValue) {
        return repository.findByIdentityTypeAndIdentityValue(identityType, identityValue).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProfileIdentityLinkResponse deactivate(Long id) {
        log.info("Deactivating profile identity link with id: {}", id);
        ProfileIdentityLink entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("IDENTITY_LINK_NOT_FOUND", "Identity link not found with id: " + id));
        entity.setStatus((short) 2);
        return toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public ProfileIdentityLinkResponse changeStatus(Long id, Short status) {
        log.info("Changing status of profile identity link id: {} to: {}", id, status);
        ProfileIdentityLink entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("IDENTITY_LINK_NOT_FOUND", "Identity link not found with id: " + id));
        entity.setStatus(status);
        return toResponse(repository.save(entity));
    }

    private ProfileIdentityLinkResponse toResponse(ProfileIdentityLink entity) {
        return ProfileIdentityLinkResponse.builder()
                .id(entity.getId())
                .masterProfileId(entity.getMasterProfileId())
                .sourceSystem(entity.getSourceSystem())
                .sourceCustomerId(entity.getSourceCustomerId())
                .identityType(entity.getIdentityType())
                .identityValue(entity.getIdentityValue())
                .confidenceScore(entity.getConfidenceScore())
                .isPrimary(entity.getIsPrimary())
                .status(entity.getStatus())
                .linkedAt(entity.getLinkedAt())
                .linkedBy(entity.getLinkedBy())
                .createdBy(entity.getCreatedBy())
                .created(entity.getCreated())
                .modified(entity.getModified())
                .modifiedBy(entity.getModifiedBy())
                .build();
    }
}
