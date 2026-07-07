package vn.vnpost.cdp.unomi.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.unomi.dto.UnomiEventCollectorPayload;
import vn.vnpost.cdp.unomi.dto.UnomiEventItem;
import vn.vnpost.cdp.unomi.dto.UnomiEventRequest;
import vn.vnpost.cdp.unomi.dto.UnomiProfileRequest;

@Slf4j
@Component
public class UnomiClient {

    private final WebClient unomiWebClient;

    public UnomiClient(@Qualifier("unomiWebClient") WebClient unomiWebClient) {
        this.unomiWebClient = unomiWebClient;
    }

    public Mono<Object> getProfile(String profileId) {
        log.info("UnomiClient - getProfile: profileId={}", profileId);
        return unomiWebClient.get()
                .uri("/cxs/profiles/{profileId}", profileId)
                .retrieve()
                .bodyToMono(Object.class)
                .doOnSuccess(res -> log.info("UnomiClient - getProfile success: profileId={}", profileId))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("UnomiClient - getProfile error: profileId={}, status={}, body={}",
                            profileId, ex.getStatusCode(), ex.getResponseBodyAsString());
                    if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.error(new BusinessException(
                                "PROFILE_NOT_FOUND_IN_UNOMI",
                                "Profile not found in Unomi: " + profileId));
                    }
                    return Mono.error(new BusinessException(
                            "UNOMI_ERROR",
                            "Unomi API error [" + ex.getStatusCode() + "]: " + ex.getResponseBodyAsString()));
                })
                .onErrorResume(BusinessException.class, Mono::error)
                .onErrorResume(Exception.class, ex -> {
                    log.error("UnomiClient - getProfile unexpected error: profileId={}", profileId, ex);
                    return Mono.error(new BusinessException(
                            "UNOMI_COMMUNICATION_ERROR",
                            "Failed to communicate with Unomi: " + ex.getMessage()));
                });
    }

    public Mono<Object> createOrUpdateProfile(UnomiProfileRequest request) {
        log.info("UnomiClient - createOrUpdateProfile: itemId={}, itemType={}",
                request.getItemId(), request.getItemType());
        return unomiWebClient.post()
                .uri("/cxs/profiles")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Object.class)
                .doOnSuccess(res -> log.info("UnomiClient - createOrUpdateProfile success: itemId={}",
                        request.getItemId()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("UnomiClient - createOrUpdateProfile error: itemId={}, status={}, body={}",
                            request.getItemId(), ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.error(new BusinessException(
                            "UNOMI_ERROR",
                            "Unomi API error [" + ex.getStatusCode() + "]: " + ex.getResponseBodyAsString()));
                })
                .onErrorResume(BusinessException.class, Mono::error)
                .onErrorResume(Exception.class, ex -> {
                    log.error("UnomiClient - createOrUpdateProfile unexpected error: itemId={}",
                            request.getItemId(), ex);
                    return Mono.error(new BusinessException(
                            "UNOMI_COMMUNICATION_ERROR",
                            "Failed to communicate with Unomi: " + ex.getMessage()));
                });
    }

    public Mono<Object> sendEvent(UnomiEventRequest request) {
        log.info("UnomiClient - sendEvent: eventType={}, profileId={}",
                request.getEventType(), request.getProfileId());

        UnomiEventItem eventItem = UnomiEventItem.builder()
                .eventType(request.getEventType())
                .scope(request.getScope())
                .source(request.getSource())
                .target(request.getTarget())
                .properties(request.getProperties())
                .build();

        UnomiEventCollectorPayload payload = UnomiEventCollectorPayload.builder()
                .sessionId(request.getSessionId())
                .profileId(request.getProfileId())
                .events(java.util.List.of(eventItem))
                .build();
        try {
            ObjectMapper mapper = new ObjectMapper();
            log.info("Payload = {}", mapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.warn("Cannot serialize payload", e);
        }
        return unomiWebClient.post()
                .uri("/cxs/eventcollector")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Object.class)
                .doOnSuccess(res -> log.info("UnomiClient - sendEvent success: eventType={}, profileId={}",
                        request.getEventType(), request.getProfileId()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("UnomiClient - sendEvent error: eventType={}, profileId={}, status={}, body={}",
                            request.getEventType(), request.getProfileId(),
                            ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.error(new BusinessException(
                            "UNOMI_ERROR",
                            "Unomi API error [" + ex.getStatusCode() + "]: " + ex.getResponseBodyAsString()));
                })
                .onErrorResume(BusinessException.class, Mono::error)
                .onErrorResume(Exception.class, ex -> {
                    log.error("UnomiClient - sendEvent unexpected error: eventType={}, profileId={}",
                            request.getEventType(), request.getProfileId(), ex);
                    return Mono.error(new BusinessException(
                            "UNOMI_COMMUNICATION_ERROR",
                            "Failed to communicate with Unomi: " + ex.getMessage()));
                });
    }

    public Mono<Object> deleteProfile(String profileId) {
        log.info("UnomiClient - deleteProfile: profileId={}", profileId);
        return unomiWebClient.delete()
                .uri("/cxs/profiles/{profileId}", profileId)
                .retrieve()
                .bodyToMono(Object.class)
                .doOnSuccess(res -> log.info("UnomiClient - deleteProfile success: profileId={}", profileId))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("UnomiClient - deleteProfile error: profileId={}, status={}, body={}",
                            profileId, ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.error(new BusinessException(
                            "UNOMI_ERROR",
                            "Unomi API error [" + ex.getStatusCode() + "]: " + ex.getResponseBodyAsString()));
                })
                .onErrorResume(BusinessException.class, Mono::error)
                .onErrorResume(Exception.class, ex -> {
                    log.error("UnomiClient - deleteProfile unexpected error: profileId={}", profileId, ex);
                    return Mono.error(new BusinessException(
                            "UNOMI_COMMUNICATION_ERROR",
                            "Failed to communicate with Unomi: " + ex.getMessage()));
                });
    }
}
