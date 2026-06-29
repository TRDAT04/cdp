package vn.vnpost.cdp.profile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.profile.dto.ProfileSourceSystemCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileSourceSystemResponse;
import vn.vnpost.cdp.profile.dto.ProfileSourceSystemUpdateRequest;
import vn.vnpost.cdp.profile.entity.ProfileSourceSystem;
import vn.vnpost.cdp.profile.repository.ProfileSourceSystemRepository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProfileSourceSystemServiceImpl implements ProfileSourceSystemService {

    private final ProfileSourceSystemRepository repository;

    public ProfileSourceSystemServiceImpl(ProfileSourceSystemRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ProfileSourceSystemResponse create(ProfileSourceSystemCreateRequest request) {
        log.info("Creating profile source system with code: {}", request.getCode());
        if (repository.existsByCode(request.getCode())) {
            throw new BusinessException("SOURCE_SYSTEM_CODE_DUPLICATED", "Source system code already exists: " + request.getCode());
        }
        ProfileSourceSystem entity = new ProfileSourceSystem();
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setSourceType(request.getSourceType());
        entity.setPriority(request.getPriority());
        entity.setStatus((short) 1);
        ProfileSourceSystem saved = repository.save(entity);
        log.info("Created profile source system with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProfileSourceSystemResponse update(Long id, ProfileSourceSystemUpdateRequest request) {
        log.info("Updating profile source system with id: {}", id);
        ProfileSourceSystem entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("SOURCE_SYSTEM_NOT_FOUND", "Source system not found with id: " + id));
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getSourceType() != null) {
            entity.setSourceType(request.getSourceType());
        }
        if (request.getPriority() != null) {
            entity.setPriority(request.getPriority());
        }
        ProfileSourceSystem saved = repository.save(entity);
        log.info("Updated profile source system with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public ProfileSourceSystemResponse getById(Long id) {
        ProfileSourceSystem entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("SOURCE_SYSTEM_NOT_FOUND", "Source system not found with id: " + id));
        return toResponse(entity);
    }

    @Override
    public ProfileSourceSystemResponse getByCode(String code) {
        ProfileSourceSystem entity = repository.findByCode(code)
                .orElseThrow(() -> new BusinessException("SOURCE_SYSTEM_NOT_FOUND", "Source system not found with code: " + code));
        return toResponse(entity);
    }

    @Override
    public List<ProfileSourceSystemResponse> listAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProfileSourceSystemResponse changeStatus(Long id, Short status) {
        log.info("Changing status of profile source system id: {} to: {}", id, status);
        ProfileSourceSystem entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("SOURCE_SYSTEM_NOT_FOUND", "Source system not found with id: " + id));
        entity.setStatus(status);
        return toResponse(repository.save(entity));
    }

    private ProfileSourceSystemResponse toResponse(ProfileSourceSystem entity) {
        return ProfileSourceSystemResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .sourceType(entity.getSourceType())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .createdBy(entity.getCreatedBy())
                .created(entity.getCreated())
                .modified(entity.getModified())
                .modifiedBy(entity.getModifiedBy())
                .build();
    }
}
