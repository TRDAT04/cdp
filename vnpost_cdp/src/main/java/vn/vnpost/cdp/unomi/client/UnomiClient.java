package vn.vnpost.cdp.unomi.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.unomi.builder.UnomiQueryBuilder;
import vn.vnpost.cdp.unomi.dto.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class UnomiClient {

    private final WebClient unomiWebClient;
    private final UnomiQueryBuilder unomiQueryBuilder;

    public UnomiClient(@Qualifier("unomiWebClient") WebClient unomiWebClient,
                       UnomiQueryBuilder unomiQueryBuilder) {
        this.unomiWebClient = unomiWebClient;
        this.unomiQueryBuilder = unomiQueryBuilder;
    }


    public Mono<UnomiProfileSearchResponse> searchProfiles(int offset, int limit) {

        Map<String, Object> body = new HashMap<>();

        body.put("condition", Map.of(
                "type", "matchAllCondition"
        ));
        body.put("offset", offset);
        body.put("limit", limit);

        return unomiWebClient
                .post()
                .uri("/cxs/profiles/search")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(UnomiProfileSearchResponse.class);
    }


    public Mono<UnomiProfileSearchResponse> searchProfilesByCodes(List<String> profileCodes) {
        if (CollectionUtils.isEmpty(profileCodes)) {
            return Mono.just(
                    UnomiProfileSearchResponse.builder()
                            .list(Collections.emptyList())
                            .build()
            );
        }

        log.debug("UnomiClient - searchProfilesByCodes: count={}", profileCodes.size());
        UnomiProfileSearchRequest request = unomiQueryBuilder.buildSearchByProfileCodes(profileCodes);

        return unomiWebClient
                .post()
                .uri("/cxs/profiles/search")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UnomiProfileSearchResponse.class)
                .doOnSuccess(res -> log.debug(
                        "UnomiClient - searchProfilesByCodes success: requested={}, returned={}",
                        profileCodes.size(),
                        res != null && res.getList() != null ? res.getList().size() : 0))
                .onErrorResume(ex -> {
                    log.warn("UnomiClient - searchProfilesByCodes failed, will degrade gracefully. Error: {}",
                            ex.getMessage());

                    return Mono.just(
                            UnomiProfileSearchResponse.builder()
                                    .list(Collections.emptyList())
                                    .build()
                    );
                });
    }

    public Mono<UnomiProfileResponse> getProfileByItemId(String itemId) {

        return unomiWebClient.get()
                .uri("/cxs/profiles/{itemId}", itemId)
                .retrieve()
                .bodyToMono(UnomiProfileResponse.class);
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

    public Mono<Object> createEventSchema(Map<String, Object> schema) {

        String eventType = String.valueOf(
                ((Map<?, ?>) schema.get("self")).get("name"));

        return unomiWebClient.post()
                .uri("/cxs/jsonSchema")
                .bodyValue(schema)
                .retrieve()
                .bodyToMono(Object.class)
                .doOnSuccess(res ->
                        log.info("Create event schema success: eventType={}", eventType))
                .doOnError(WebClientResponseException.class, ex ->
                        log.error("Create event schema failed: eventType={}, status={}, body={}",
                                eventType,
                                ex.getStatusCode(),
                                ex.getResponseBodyAsString()));
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



    public Mono<List<UnomiSegmentResponse>> getSegments() {

        return unomiWebClient
                .get()
                .uri("/cxs/segments")
                .retrieve()
                .bodyToFlux(UnomiSegmentResponse.class)
                .collectList();
    }
    public Mono<UnomiSegmentDetailResponse> getSegmentDetail(String segmentId) {

        return unomiWebClient
                .get()
                .uri("/cxs/segments/{segmentId}", segmentId)
                .retrieve()
                .bodyToMono(UnomiSegmentDetailResponse.class);
    }
    public Mono<UnomiProfileSearchResponse> getSegmentMembers(String segmentId, int limit) {
        if (segmentId == null || segmentId.isBlank()) {
            return Mono.just(
                    UnomiProfileSearchResponse.builder()
                            .list(Collections.emptyList())
                            .build()
            );
        }

        log.debug("UnomiClient - getSegmentMembers: segmentId={}, limit={}", segmentId, limit);
        UnomiProfileSearchRequest request = unomiQueryBuilder.buildSearchBySegment(segmentId, limit);

        return unomiWebClient.post()
                .uri("/cxs/profiles/search")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UnomiProfileSearchResponse.class)
                .doOnSuccess(res -> log.debug(
                        "UnomiClient - getSegmentMembers success: segmentId={}, returned={}",
                        segmentId,
                        res != null && res.getList() != null ? res.getList().size() : 0))
                .onErrorResume(ex -> {
                    log.warn("UnomiClient - getSegmentMembers failed, will degrade gracefully. segmentId={}, error={}",
                            segmentId, ex.getMessage());

                    return Mono.just(
                            UnomiProfileSearchResponse.builder()
                                    .list(Collections.emptyList())
                                    .build()
                    );
                });
    }
}
