package vn.vnpost.cdp.profile.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.vnpost.cdp.profile.entity.ProfileAttributeValue;
import vn.vnpost.cdp.profile.entity.ProfileMergeRule;
import vn.vnpost.cdp.profile.repository.ProfileAttributeValueRepository;
import vn.vnpost.cdp.profile.repository.ProfileMergeRuleRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileMergeEngineServiceImpl implements ProfileMergeEngineService {

    private final ProfileMergeRuleRepository ruleRepository;
    private final ProfileAttributeValueRepository attributeRepository;

    @Override
    public boolean shouldOverwrite(Long masterProfileId, String propertyName, String incomingSource) {
        log.info("MergeEngine - evaluating overwrite: masterProfileId={}, property={}, incomingSource={}",
                masterProfileId, propertyName, incomingSource);

        ProfileAttributeValue selected = attributeRepository
                .findFirstByMasterProfileIdAndPropertyNameAndIsSelectedTrue(masterProfileId, propertyName)
                .orElse(null);

        if (selected == null) {
            log.info("MergeEngine - no selected value -> allow overwrite");
            return true;
        }

        String currentSource = selected.getSourceSystem();

        if (incomingSource.equalsIgnoreCase(currentSource)) {
            log.info("MergeEngine - same source -> allow overwrite");
            return true;
        }

        ProfileMergeRule currentRule = ruleRepository
                .findByPropertyNameAndSourceSystemAndStatus(propertyName, currentSource, (short) 1)
                .orElse(null);

        ProfileMergeRule incomingRule = ruleRepository
                .findByPropertyNameAndSourceSystemAndStatus(propertyName, incomingSource, (short) 1)
                .orElse(null);

        if (currentRule == null || incomingRule == null) {
            log.warn("MergeEngine - missing rule config -> deny overwrite");
            return false;
        }

        // =========================
        // 1. REQUIRE REVIEW check
        // =========================
        if (Boolean.TRUE.equals(currentRule.getRequireReview())) {
            log.info("MergeEngine - current requires review -> block overwrite");
            return false;
        }

        if (Boolean.TRUE.equals(incomingRule.getRequireReview())) {
            log.info("MergeEngine - incoming requires review -> block overwrite");
            return false;
        }

        // =========================
        // 2. MERGE STRATEGY check
        // =========================
        String strategy = incomingRule.getMergeStrategy();

        if (strategy != null) {

            switch (strategy) {

                case "MANUAL_ONLY":
                    log.info("MergeEngine - MANUAL_ONLY -> block overwrite");
                    return false;

                case "LATEST_UPDATE":
                    // bạn chưa có timestamp trong selected -> tạm fallback priority
                    log.info("MergeEngine - LATEST_UPDATE -> fallback to priority");
                    break;

                case "SOURCE_PRIORITY":
                    Integer currentPriority = currentRule.getPriority();
                    Integer incomingPriority = incomingRule.getPriority();

                    if (currentPriority != null && incomingPriority != null) {
                        boolean result = incomingPriority < currentPriority;
                        log.info("MergeEngine - SOURCE_PRIORITY decision={}", result);
                        return result;
                    }
                    break;

                case "APPEND_LIST":
                case "SUM":
                case "MAX":
                case "MIN":
                    log.info("MergeEngine - non-overwrite strategy -> block overwrite");
                    return false;
            }
        }

        // =========================
        // 3. FALLBACK: allowOverwrite flag
        // =========================
        if (Boolean.FALSE.equals(incomingRule.getAllowOverwrite())) {
            log.info("MergeEngine - allowOverwrite=false -> block overwrite");
            return false;
        }

        // =========================
        // 4. FINAL fallback: priority
        // =========================
        Integer currentPriority = currentRule.getPriority();
        Integer incomingPriority = incomingRule.getPriority();

        if (currentPriority == null || incomingPriority == null) {
            log.warn("MergeEngine - missing priority -> deny overwrite");
            return false;
        }

        boolean result = incomingPriority < currentPriority;

        log.info("MergeEngine - fallback priority decision={}", result);

        return result;
    }
}