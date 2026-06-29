package vn.vnpost.cdp.profile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.profile.dto.*;
import vn.vnpost.cdp.profile.entity.ProfileMergeRule;
import vn.vnpost.cdp.profile.repository.ProfileMergeRuleRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProfileMergeRuleServiceImpl implements ProfileMergeRuleService {

    private final ProfileMergeRuleRepository repository;

    public ProfileMergeRuleServiceImpl(ProfileMergeRuleRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ProfileMergeRuleResponse create(ProfileMergeRuleCreateRequest request) {
        log.info("Creating ProfileMergeRule for propertyName={}", request.getPropertyName());
        ProfileMergeRule entity = new ProfileMergeRule();
        entity.setPropertyName(request.getPropertyName());
        entity.setSourceSystem(request.getSourceSystem());
        entity.setPriority(request.getPriority());
        entity.setMergeStrategy(request.getMergeStrategy());
        entity.setAllowOverwrite(request.getAllowOverwrite());
        entity.setRequireReview(request.getRequireReview());
        entity.setDescription(request.getDescription());
        entity.setStatus((short) 1);
        ProfileMergeRule saved = repository.save(entity);
        log.info("Created ProfileMergeRule id={}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProfileMergeRuleResponse update(Long id, ProfileMergeRuleUpdateRequest request) {
        log.info("Updating ProfileMergeRule id={}", id);
        ProfileMergeRule entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_RULE_NOT_FOUND", "Profile merge rule not found with id: " + id));
        if (request.getSourceSystem() != null) {
            entity.setSourceSystem(request.getSourceSystem());
        }
        if (request.getPriority() != null) {
            entity.setPriority(request.getPriority());
        }
        if (request.getMergeStrategy() != null) {
            entity.setMergeStrategy(request.getMergeStrategy());
        }
        if (request.getAllowOverwrite() != null) {
            entity.setAllowOverwrite(request.getAllowOverwrite());
        }
        if (request.getRequireReview() != null) {
            entity.setRequireReview(request.getRequireReview());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        ProfileMergeRule saved = repository.save(entity);
        log.info("Updated ProfileMergeRule id={}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public ProfileMergeRuleResponse getById(Long id) {
        log.info("Getting ProfileMergeRule id={}", id);
        ProfileMergeRule entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_RULE_NOT_FOUND", "Profile merge rule not found with id: " + id));
        return toResponse(entity);
    }

    @Override
    public List<ProfileMergeRuleResponse> listActive() {
        log.info("Listing active ProfileMergeRules");
        return repository.findByStatus((short) 1)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfileMergeRuleResponse> listByPropertyName(String propertyName) {
        log.info("Listing ProfileMergeRules for propertyName={}", propertyName);
        return repository.findByPropertyNameAndStatusOrderByPriorityAsc(propertyName, (short) 1)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProfileMergeRuleResponse changeStatus(Long id, Short status) {
        log.info("Changing status of ProfileMergeRule id={} to status={}", id, status);
        ProfileMergeRule entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException("MERGE_RULE_NOT_FOUND", "Profile merge rule not found with id: " + id));
        entity.setStatus(status);
        ProfileMergeRule saved = repository.save(entity);
        log.info("Changed status of ProfileMergeRule id={}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public ProfileMergeRuleTestResponse testRule(ProfileMergeRuleTestRequest request) {
        log.info("Testing merge rule for propertyName={}, currentSource={}, incomingSource={}",
                request.getPropertyName(), request.getCurrentSource(), request.getIncomingSource());

        List<ProfileMergeRule> rules = repository.findByPropertyNameAndStatusOrderByPriorityAsc(
                request.getPropertyName(), (short) 1);

        Optional<ProfileMergeRule> currentRule = rules.stream()
                .filter(r -> request.getCurrentSource().equalsIgnoreCase(r.getSourceSystem()))
                .findFirst();
        Optional<ProfileMergeRule> incomingRule = rules.stream()
                .filter(r -> request.getIncomingSource().equalsIgnoreCase(r.getSourceSystem()))
                .findFirst();

        if (currentRule.isPresent() && incomingRule.isPresent()) {
            int currentPriority = currentRule.get().getPriority() != null ? currentRule.get().getPriority() : Integer.MAX_VALUE;
            int incomingPriority = incomingRule.get().getPriority() != null ? incomingRule.get().getPriority() : Integer.MAX_VALUE;
            if (incomingPriority < currentPriority) {
                return ProfileMergeRuleTestResponse.builder()
                        .selectedValue(request.getIncomingValue())
                        .selectedSource(request.getIncomingSource())
                        .reason(request.getIncomingSource() + " has higher priority than " + request.getCurrentSource())
                        .build();
            } else {
                return ProfileMergeRuleTestResponse.builder()
                        .selectedValue(request.getCurrentValue())
                        .selectedSource(request.getCurrentSource())
                        .reason(request.getCurrentSource() + " has higher priority than " + request.getIncomingSource())
                        .build();
            }
        } else if (incomingRule.isPresent()) {
            return ProfileMergeRuleTestResponse.builder()
                    .selectedValue(request.getIncomingValue())
                    .selectedSource(request.getIncomingSource())
                    .reason("Only incoming source " + request.getIncomingSource() + " has a configured rule")
                    .build();
        } else if (currentRule.isPresent()) {
            return ProfileMergeRuleTestResponse.builder()
                    .selectedValue(request.getCurrentValue())
                    .selectedSource(request.getCurrentSource())
                    .reason("Only current source " + request.getCurrentSource() + " has a configured rule")
                    .build();
        } else {
            return ProfileMergeRuleTestResponse.builder()
                    .selectedValue(request.getIncomingValue())
                    .selectedSource(request.getIncomingSource())
                    .reason("No merge rule found, default to incoming value")
                    .build();
        }
    }

    private ProfileMergeRuleResponse toResponse(ProfileMergeRule entity) {
        return ProfileMergeRuleResponse.builder()
                .id(entity.getId())
                .propertyName(entity.getPropertyName())
                .sourceSystem(entity.getSourceSystem())
                .priority(entity.getPriority())
                .mergeStrategy(entity.getMergeStrategy())
                .allowOverwrite(entity.getAllowOverwrite())
                .requireReview(entity.getRequireReview())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .createdBy(entity.getCreatedBy())
                .created(entity.getCreated())
                .modified(entity.getModified())
                .modifiedBy(entity.getModifiedBy())
                .build();
    }
}
