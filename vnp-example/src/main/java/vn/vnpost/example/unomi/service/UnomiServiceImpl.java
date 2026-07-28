package vn.vnpost.example.unomi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import vn.vnpost.example.profile.entity.MasterProfile;
import vn.vnpost.example.unomi.client.UnomiClient;
import vn.vnpost.example.unomi.dto.UnomiEventRequest;
import vn.vnpost.example.unomi.dto.UnomiProfileRequest;

import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class UnomiServiceImpl implements UnomiService {

    private final UnomiClient unomiClient;

    public UnomiServiceImpl(UnomiClient unomiClient) {
        this.unomiClient = unomiClient;
    }

    @Override
    public Mono<Object> syncProfileToUnomi(MasterProfile profile) {
        log.info("UnomiService - syncProfileToUnomi: profileCode={}", profile.getProfileCode());

        Map<String, Object> properties = new HashMap<>();
        properties.put("cdpProfileId", profile.getId());
        properties.put("cdpProfileCode", profile.getProfileCode());
        properties.put("fullName", profile.getFullName());
        properties.put("phone", profile.getPhone());
        properties.put("email", profile.getEmail());
        properties.put("gender", profile.getGender());
        properties.put("dateOfBirth", profile.getDateOfBirth() != null
                ? profile.getDateOfBirth().toString() : null);
        properties.put("identityNo", profile.getIdentityNo());
        properties.put("customerType", profile.getCustomerType());
        properties.put("provinceCode", profile.getProvinceCode());
        properties.put("provinceName", profile.getProvinceName());
        properties.put("unitCode", profile.getUnitCode());
        properties.put("unitName", profile.getUnitName());
        properties.put("status", profile.getStatus());
        properties.put(
                "createdAt",
                profile.getCreated()
                        .atZone(ZoneOffset.UTC)
                        .toInstant()
                        .truncatedTo(ChronoUnit.MILLIS)
                        .toString()
        );
        UnomiProfileRequest request = UnomiProfileRequest.builder()
                .itemId(profile.getProfileCode())
                .itemType("profile")
                .properties(properties)
                .build();

        return unomiClient.createOrUpdateProfile(request)
                .doOnSuccess(res -> log.info("UnomiService - syncProfileToUnomi success: profileCode={}",
                        profile.getProfileCode()))
                .doOnError(ex -> log.error("UnomiService - syncProfileToUnomi error: profileCode={}",
                        profile.getProfileCode(), ex));
    }

    @Override
    public Mono<Object> sendEventToUnomi(UnomiEventRequest request) {
        log.info("UnomiService - sendEventToUnomi: eventType={}, profileId={}",
                request.getEventType(),
                request.getProfileId());

        return unomiClient.sendEvent(request)
                .doOnSuccess(res ->
                        log.info("UnomiService - sendEventToUnomi success"))
                .doOnError(ex ->
                        log.error("UnomiService - sendEventToUnomi error", ex));
    }
}
