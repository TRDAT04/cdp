package vn.vnpost.cdp.profile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.profile.dto.ProfileMergeJobCreateRequest;
import vn.vnpost.cdp.profile.dto.ProfileMergeJobResponse;
import vn.vnpost.cdp.profile.dto.ProfileMergeJobUpdateRequest;
import vn.vnpost.cdp.profile.entity.ProfileMergeJob;
import vn.vnpost.cdp.profile.repository.ProfileMergeJobRepository;
import vn.vnpost.cdp.profile.service.ProfileMergeJobService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProfileMergeJobServiceImpl implements ProfileMergeJobService {

    private final ProfileMergeJobRepository repository;

    public ProfileMergeJobServiceImpl(ProfileMergeJobRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ProfileMergeJobResponse create(ProfileMergeJobCreateRequest request) {
        log.info("Creating profile merge job jobType={}", request.getJobType());
        ProfileMergeJob entity = new ProfileMergeJob();
        entity.setJobType(request.getJobType());
        entity.setSourceSystem(request.getSourceSystem());
        entity.setTotalRecords(request.getTotalRecords());
        entity.setStatus((short) 0);
        entity = repository.save(entity);
        log.info("Created profile merge job id={}", entity.getId());
        return toResponse(entity);
    }

    @Override
    public ProfileMergeJobResponse getById(Long id) {
        log.info("Getting profile merge job id={}", id);
        ProfileMergeJob entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_JOB_NOT_FOUND", "Profile merge job not found with id: " + id));
        return toResponse(entity);
    }

    @Override
    public List<ProfileMergeJobResponse> listByJobType(String jobType) {
        log.info("Listing profile merge jobs by jobType={}", jobType);
        return repository.findByJobType(jobType).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfileMergeJobResponse> listByStatus(Short status) {
        log.info("Listing profile merge jobs by status={}", status);
        return repository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProfileMergeJobResponse markRunning(Long id) {
        log.info("Marking profile merge job id={} as RUNNING", id);
        ProfileMergeJob entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_JOB_NOT_FOUND", "Profile merge job not found with id: " + id));
        entity.setStatus((short) 1);
        entity.setStartedAt(LocalDateTime.now());
        entity = repository.save(entity);
        log.info("Marked profile merge job id={} as RUNNING", entity.getId());
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ProfileMergeJobResponse markSuccess(Long id, ProfileMergeJobUpdateRequest request) {
        log.info("Marking profile merge job id={} as SUCCESS", id);
        ProfileMergeJob entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_JOB_NOT_FOUND", "Profile merge job not found with id: " + id));
        entity.setStatus((short) 2);
        entity.setFinishedAt(LocalDateTime.now());
        if (request.getSuccessRecords() != null) {
            entity.setSuccessRecords(request.getSuccessRecords());
        }
        if (request.getConflictRecords() != null) {
            entity.setConflictRecords(request.getConflictRecords());
        }
        if (request.getFailedRecords() != null) {
            entity.setFailedRecords(request.getFailedRecords());
        }
        entity = repository.save(entity);
        log.info("Marked profile merge job id={} as SUCCESS", entity.getId());
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ProfileMergeJobResponse markFailed(Long id, String errorMessage) {
        log.info("Marking profile merge job id={} as FAILED", id);
        ProfileMergeJob entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_JOB_NOT_FOUND", "Profile merge job not found with id: " + id));
        entity.setStatus((short) 3);
        entity.setFinishedAt(LocalDateTime.now());
        entity.setErrorMessage(errorMessage);
        entity = repository.save(entity);
        log.info("Marked profile merge job id={} as FAILED", entity.getId());
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ProfileMergeJobResponse markPartialSuccess(Long id, ProfileMergeJobUpdateRequest request) {
        log.info("Marking profile merge job id={} as PARTIAL_SUCCESS", id);
        ProfileMergeJob entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_JOB_NOT_FOUND", "Profile merge job not found with id: " + id));
        entity.setStatus((short) 4);
        entity.setFinishedAt(LocalDateTime.now());
        if (request.getSuccessRecords() != null) {
            entity.setSuccessRecords(request.getSuccessRecords());
        }
        if (request.getConflictRecords() != null) {
            entity.setConflictRecords(request.getConflictRecords());
        }
        if (request.getFailedRecords() != null) {
            entity.setFailedRecords(request.getFailedRecords());
        }
        entity = repository.save(entity);
        log.info("Marked profile merge job id={} as PARTIAL_SUCCESS", entity.getId());
        return toResponse(entity);
    }

    private ProfileMergeJobResponse toResponse(ProfileMergeJob entity) {
        return ProfileMergeJobResponse.builder()
                .id(entity.getId())
                .jobType(entity.getJobType())
                .sourceSystem(entity.getSourceSystem())
                .totalRecords(entity.getTotalRecords())
                .successRecords(entity.getSuccessRecords())
                .conflictRecords(entity.getConflictRecords())
                .failedRecords(entity.getFailedRecords())
                .status(entity.getStatus())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .errorMessage(entity.getErrorMessage())
                .createdBy(entity.getCreatedBy())
                .created(entity.getCreated())
                .modified(entity.getModified())
                .modifiedBy(entity.getModifiedBy())
                .build();
    }
}
