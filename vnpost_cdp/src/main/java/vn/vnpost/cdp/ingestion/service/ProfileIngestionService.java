package vn.vnpost.cdp.ingestion.service;

import reactor.core.publisher.Mono;
import vn.vnpost.cdp.ingestion.dto.ProfileIngestionMessage;

public interface ProfileIngestionService {
    Mono<Void> process(ProfileIngestionMessage message);
}
