package vn.vnpost.example.ingestion.service;

import reactor.core.publisher.Mono;
import vn.vnpost.example.ingestion.dto.ProfileIngestionMessage;

public interface ProfileIngestionService {
    Mono<Void> process(ProfileIngestionMessage message);
}
