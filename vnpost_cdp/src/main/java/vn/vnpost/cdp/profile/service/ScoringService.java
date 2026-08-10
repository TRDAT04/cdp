package vn.vnpost.cdp.profile.service;

import reactor.core.publisher.Mono;
import vn.vnpost.cdp.profile.dto.query.ProfileScoringResponse;

/**
 * Tính điểm số & phân khúc cho tab "Điểm số & Phân khúc" (RFM/CLV/churn/engagement).
 */
public interface ScoringService {

    /**
     * @param id master_profiles.id
     * @return điểm số của profile; phát {@code BusinessException("NOT_FOUND", ...)} nếu không tồn tại.
     */
    Mono<ProfileScoringResponse> getProfileScoring(Long id);
}
