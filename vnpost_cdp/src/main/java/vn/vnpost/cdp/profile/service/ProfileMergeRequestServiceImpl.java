package vn.vnpost.cdp.profile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.profile.dto.ProfileMergeRequestApproveRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeRequestCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeRequestResponse;
import vn.vnpost.cdp.profile.entity.ProfileMergeRequest;
import vn.vnpost.cdp.profile.repository.ProfileMergeRequestRepository;
import vn.vnpost.cdp.profile.service.ProfileMergeRequestService;
import vn.vnpost.cdp.security.SecurityUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProfileMergeRequestServiceImpl implements ProfileMergeRequestService {

    private final ProfileMergeRequestRepository repository;

    public ProfileMergeRequestServiceImpl(ProfileMergeRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ProfileMergeRequestResponse create(ProfileMergeRequestCreateRequest request) {
        log.info("Creating profile merge request from sourceProfileId={} to targetProfileId={}",
                request.getSourceMasterProfileId(), request.getTargetMasterProfileId());
        ProfileMergeRequest entity = new ProfileMergeRequest();
        entity.setSourceMasterProfileId(request.getSourceMasterProfileId());
        entity.setTargetMasterProfileId(request.getTargetMasterProfileId());
        entity.setMergeReason(request.getMergeReason());
        entity.setSelectedValues(request.getSelectedValues());
        entity.setStatus((short) 0);
        entity.setRequestedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        entity.setRequestedAt(LocalDateTime.now());
        entity = repository.save(entity);
        log.info("Created profile merge request id={}", entity.getId());
        return toResponse(entity);
    }

    @Override
    public ProfileMergeRequestResponse getById(Long id) {
        log.info("Getting profile merge request id={}", id);
        ProfileMergeRequest entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_REQUEST_NOT_FOUND", "Profile merge request not found with id: " + id));
        return toResponse(entity);
    }

    @Override
    public List<ProfileMergeRequestResponse> listByStatus(Short status) {
        log.info("Listing profile merge requests by status={}", status);
        return repository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfileMergeRequestResponse> listBySourceProfile(Long sourceMasterProfileId) {
        log.info("Listing profile merge requests by sourceMasterProfileId={}", sourceMasterProfileId);
        return repository.findBySourceMasterProfileId(sourceMasterProfileId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfileMergeRequestResponse> listByTargetProfile(Long targetMasterProfileId) {
        log.info("Listing profile merge requests by targetMasterProfileId={}", targetMasterProfileId);
        return repository.findByTargetMasterProfileId(targetMasterProfileId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProfileMergeRequestResponse approve(Long id, ProfileMergeRequestApproveRequest request) {
        log.info("Approving profile merge request id={}", id);
        ProfileMergeRequest entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_REQUEST_NOT_FOUND", "Profile merge request not found with id: " + id));
        if (entity.getStatus() != 0) {
            throw new BusinessException("MERGE_REQUEST_CANNOT_APPROVE", "Merge request cannot be approved in current status");
        }
        entity.setStatus((short) 1);
        String approvedBy = (request.getApprovedBy() != null && !request.getApprovedBy().isBlank())
                ? request.getApprovedBy()
                : SecurityUtils.getCurrentUsername().orElse("system");
        entity.setApprovedBy(approvedBy);
        entity.setApprovedAt(LocalDateTime.now());
        entity = repository.save(entity);
        log.info("Approved profile merge request id={}", entity.getId());
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ProfileMergeRequestResponse reject(Long id) {
        log.info("Rejecting profile merge request id={}", id);
        ProfileMergeRequest entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_REQUEST_NOT_FOUND", "Profile merge request not found with id: " + id));
        if (entity.getStatus() != 0) {
            throw new BusinessException("MERGE_REQUEST_CANNOT_REJECT", "Merge request cannot be rejected in current status");
        }
        entity.setStatus((short) 2);
        entity = repository.save(entity);
        log.info("Rejected profile merge request id={}", entity.getId());
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ProfileMergeRequestResponse complete(Long id) {
        log.info("Completing profile merge request id={}", id);
        ProfileMergeRequest entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_REQUEST_NOT_FOUND", "Profile merge request not found with id: " + id));
        if (entity.getStatus() != 1) {
            throw new BusinessException("MERGE_REQUEST_CANNOT_COMPLETE", "Merge request must be approved before completing");
        }
        entity.setStatus((short) 3);
        entity.setCompletedAt(LocalDateTime.now());
        entity = repository.save(entity);
        log.info("Completed profile merge request id={}", entity.getId());
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ProfileMergeRequestResponse fail(Long id, String errorMessage) {
        log.info("Failing profile merge request id={}", id);
        ProfileMergeRequest entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_REQUEST_NOT_FOUND", "Profile merge request not found with id: " + id));
        entity.setStatus((short) 4);
        entity.setErrorMessage(errorMessage);
        entity = repository.save(entity);
        log.info("Failed profile merge request id={}", entity.getId());
        return toResponse(entity);
    }

    private ProfileMergeRequestResponse toResponse(ProfileMergeRequest entity) {
        return ProfileMergeRequestResponse.builder()
                .id(entity.getId())
                .sourceMasterProfileId(entity.getSourceMasterProfileId())
                .targetMasterProfileId(entity.getTargetMasterProfileId())
                .mergeReason(entity.getMergeReason())
                .selectedValues(entity.getSelectedValues())
                .status(entity.getStatus())
                .requestedBy(entity.getRequestedBy())
                .approvedBy(entity.getApprovedBy())
                .requestedAt(entity.getRequestedAt())
                .approvedAt(entity.getApprovedAt())
                .completedAt(entity.getCompletedAt())
                .errorMessage(entity.getErrorMessage())
                .createdBy(entity.getCreatedBy())
                .created(entity.getCreated())
                .modified(entity.getModified())
                .modifiedBy(entity.getModifiedBy())
                .build();
    }
}
