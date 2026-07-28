package vn.vnpost.example.profile.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import vn.vnpost.example.profile.entity.ProfileAttributeValue;
import vn.vnpost.example.profile.entity.ProfileMergeRule;
import vn.vnpost.example.profile.repository.ProfileAttributeValueRepository;
import vn.vnpost.example.profile.repository.ProfileMergeRuleRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileMergeEngineServiceImpl implements ProfileMergeEngineService {

    private final ProfileMergeRuleRepository ruleRepository;
    private final ProfileAttributeValueRepository attributeRepository;

    @Override
    public Mono<Boolean> shouldOverwrite(Long masterProfileId, String propertyName, String incomingSource,
                                          LocalDateTime incomingReceivedAt) {
        log.info("MergeEngine - evaluating overwrite: masterProfileId={}, property={}, incomingSource={}",
                masterProfileId, propertyName, incomingSource);

        return attributeRepository
                .findFirstByMasterProfileIdAndPropertyNameAndIsSelectedTrue(masterProfileId, propertyName)
                .flatMap(selected -> {
                    String currentSource = selected.getSourceSystem();

                    if (incomingSource.equalsIgnoreCase(currentSource)) {
                        log.info("MergeEngine - same source -> allow overwrite");
                        return Mono.just(true);
                    }

                    Mono<ProfileMergeRule> currentRuleMono = ruleRepository
                            .findByPropertyNameAndSourceSystemAndStatus(propertyName, currentSource, (short) 1);
                    Mono<ProfileMergeRule> incomingRuleMono = ruleRepository
                            .findByPropertyNameAndSourceSystemAndStatus(propertyName, incomingSource, (short) 1);

                    return Mono.zip(optional(currentRuleMono), optional(incomingRuleMono))
                            .map(t -> evaluate(t.getT1().orElse(null), t.getT2().orElse(null),
                                    selected, incomingReceivedAt));
                })
                .defaultIfEmpty(true); // no selected value -> allow overwrite (log matches original "no selected value" branch)
    }

    private boolean evaluate(ProfileMergeRule currentRule, ProfileMergeRule incomingRule,
                              ProfileAttributeValue selected, LocalDateTime incomingReceivedAt) {
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
                    LocalDateTime currentReceivedAt = selected.getReceivedAt();
                    if (currentReceivedAt == null) {
                        log.info("MergeEngine - current receivedAt is null -> allow overwrite");
                        return true;
                    }
                    if (incomingReceivedAt == null) {
                        log.info("MergeEngine - incoming receivedAt is null -> deny overwrite");
                        return false;
                    }
                    boolean latestResult = incomingReceivedAt.isAfter(currentReceivedAt);
                    log.info(
                            "MergeEngine - LATEST_UPDATE decision={}, current={}, incoming={}",
                            latestResult,
                            currentReceivedAt,
                            incomingReceivedAt
                    );
                    return latestResult;

                case "SOURCE_PRIORITY":
                    Integer currentPriority = currentRule.getPriority();
                    Integer incomingPriority = incomingRule.getPriority();

                    if (currentPriority != null && incomingPriority != null) {
                        boolean result = incomingPriority < currentPriority;
                        log.info("MergeEngine - SOURCE_PRIORITY decision={}", result);
                        return result;
                    }
                    break;

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

    private static <T> Mono<Optional<T>> optional(Mono<T> mono) {
        return mono.map(Optional::of).defaultIfEmpty(Optional.empty());
    }
}
