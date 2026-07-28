package vn.vnpost.example.profile.service;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ProfileMergeEngineService {
    Mono<Boolean> shouldOverwrite(
            Long masterProfileId,
            String propertyName,
            String incomingSource,
            LocalDateTime incomingReceivedAt);
}
