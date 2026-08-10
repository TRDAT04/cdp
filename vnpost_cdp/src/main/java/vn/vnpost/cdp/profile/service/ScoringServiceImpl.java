package vn.vnpost.cdp.profile.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.customer_event.entity.CustomerEvent;
import vn.vnpost.cdp.customer_event.repository.CustomerEventRepository;
import vn.vnpost.cdp.customer_event.repository.RfmScoreRow;
import vn.vnpost.cdp.profile.assembler.ProfileDetailAssembler;
import vn.vnpost.cdp.profile.dto.query.ProfileDigitalBehaviorResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileScoringResponse;
import vn.vnpost.cdp.profile.dto.query.ProfileServiceLinesResponse;
import vn.vnpost.cdp.profile.repository.MasterProfileRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringServiceImpl implements ScoringService {

    private static final int RFM_WINDOW_MONTHS = 12;
    private static final double CHURN_WINDOW_DAYS = 90.0;
    private static final int ENGAGEMENT_STEP = 20;

    private final MasterProfileRepository masterProfileRepository;
    private final CustomerEventRepository customerEventRepository;
    private final ProfileDetailAssembler profileDetailAssembler;

    @Override
    public Mono<ProfileScoringResponse> getProfileScoring(Long id) {
        log.info("Scoring profile id={}", id);
        return masterProfileRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("NOT_FOUND", "Profile not found: " + id)))
                .flatMap(profile -> {
                    LocalDateTime now = LocalDateTime.now();

                    // Đủ lịch sử (uncapped), dùng chung cho CLV, churn, engagement.
                    return customerEventRepository.findByMasterProfileIdOrderByOccurredAtDesc(profile.getId())
                            .collectList()
                            .flatMap(events -> {
                                ProfileDigitalBehaviorResponse behavior = profileDetailAssembler.assembleBehavior(events);

                                return computeRfm(profile.getId(), now)
                                        .map(Optional::of)
                                        .defaultIfEmpty(Optional.empty())
                                        .map(rfmOpt -> ProfileScoringResponse.builder()
                                                .calculatedAt(now)
                                                .rfm(rfmOpt.orElse(null))
                                                .clv(computeClv(profile.getId(), events))
                                                .churnScore(computeChurnScore(behavior, now))
                                                .engagementScore(computeEngagementScore(behavior, now))
                                                .codRiskScore(null)
                                                .fraudScore(null)
                                                .build());
                            });
                });
    }

    // ------------------------------------------------------------------
    // RFM — percentile/quintile chuẩn ngành (5 = tốt nhất)
    // ------------------------------------------------------------------

    private Mono<ProfileScoringResponse.Rfm> computeRfm(Long profileId, LocalDateTime now) {
        LocalDateTime windowStart = now.minusMonths(RFM_WINDOW_MONTHS);
        // profile tồn tại nên bình thường luôn có 1 dòng; Mono rỗng (guard) -> rfm null ở tầng gọi.
        return customerEventRepository.findRfmScores(profileId, now, windowStart)
                .map(this::toRfm);
    }

    private ProfileScoringResponse.Rfm toRfm(RfmScoreRow row) {
        int recency = row.recencyScore();
        int frequency = row.frequencyScore();
        int monetary = row.monetaryScore();

        return ProfileScoringResponse.Rfm.builder()
                .segment(resolveSegment(recency, frequency, monetary))
                .recencyScore(recency)
                .frequencyScore(frequency)
                .monetaryScore(monetary)
                .build();
    }

    /** Map 3 điểm R/F/M (1..5, 5 = tốt) ra tên nhóm. Đánh giá theo thứ tự, first-match. */
    private String resolveSegment(int r, int f, int m) {
        if (r >= 4 && f >= 4 && m >= 4) return "Champions";
        if (r >= 3 && f >= 3 && m >= 3) return "Loyal Customers";
        if (r >= 4 && f <= 2) return "New Customers";
        if (r <= 2 && f >= 4 && m >= 4) return "At Risk";
        if (r <= 2 && f <= 2 && m <= 2) return "Lost";
        if (r >= 3 && f <= 2 && m >= 4) return "Big Spenders";
        return "Regular";
    }

    // ------------------------------------------------------------------
    // CLV — tổng doanh thu 7 mảng dịch vụ (tái sử dụng assembleServiceLines)
    // ------------------------------------------------------------------

    private BigDecimal computeClv(Long profileId, List<CustomerEvent> events) {
        ProfileServiceLinesResponse serviceLines =
                profileDetailAssembler.assembleServiceLines(profileId, events);
        if (serviceLines.getServiceLines() == null) {
            return BigDecimal.ZERO;
        }
        return serviceLines.getServiceLines().stream()
                .map(ProfileServiceLinesResponse.ServiceLineBlock::getTotalRevenue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------
    // Churn — dựa trên đơn gần nhất
    // ------------------------------------------------------------------

    private Integer computeChurnScore(ProfileDigitalBehaviorResponse behavior, LocalDateTime now) {
        ProfileDigitalBehaviorResponse.RecentOrder recentOrder = behavior.getRecentOrder();
        if (recentOrder == null || recentOrder.getOccurredAt() == null) {
            return null; // chưa từng mua ⇒ không tính churn (KHÔNG gán 100)
        }
        long daysSinceLastOrder = ChronoUnit.DAYS.between(recentOrder.getOccurredAt(), now);
        if (daysSinceLastOrder < 0) daysSinceLastOrder = 0; // phòng dữ liệu occurredAt ở tương lai
        double score = Math.min(daysSinceLastOrder / CHURN_WINDOW_DAYS * 100.0, 100.0);
        return (int) Math.round(score);
    }

    // ------------------------------------------------------------------
    // Engagement — rule-based, +20/điều kiện, thiếu dữ liệu = 0 điểm
    // ------------------------------------------------------------------

    private Integer computeEngagementScore(ProfileDigitalBehaviorResponse behavior, LocalDateTime now) {
        int score = 0;

        // +20 nếu lastLoginAt trong 7 ngày qua
        if (behavior.getLastLoginAt() != null
                && !behavior.getLastLoginAt().isBefore(now.minusDays(7))) {
            score += ENGAGEMENT_STEP;
        }
        // +20 nếu sessionsLast30Days > 0
        if (behavior.getSessionsLast30Days() != null
                && behavior.getSessionsLast30Days() > 0) {
            score += ENGAGEMENT_STEP;
        }
        // +20 nếu channelsInteracted.size() >= 3
        if (behavior.getChannelsInteracted() != null
                && behavior.getChannelsInteracted().size() >= 3) {
            score += ENGAGEMENT_STEP;
        }
        // +20 nếu có recentOrder trong 30 ngày qua
        ProfileDigitalBehaviorResponse.RecentOrder recentOrder = behavior.getRecentOrder();
        if (recentOrder != null && recentOrder.getOccurredAt() != null
                && !recentOrder.getOccurredAt().isBefore(now.minusDays(30))) {
            score += ENGAGEMENT_STEP;
        }
        // +20 nếu lastCampaignResponse trong 30 ngày qua
        ProfileDigitalBehaviorResponse.LastCampaignResponse campaign = behavior.getLastCampaignResponse();
        if (campaign != null && campaign.getOccurredAt() != null
                && !campaign.getOccurredAt().isBefore(now.minusDays(30))) {
            score += ENGAGEMENT_STEP;
        }
        return score;
    }
}
