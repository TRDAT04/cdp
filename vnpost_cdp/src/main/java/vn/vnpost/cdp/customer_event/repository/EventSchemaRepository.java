package vn.vnpost.cdp.customer_event.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.customer_event.entity.EventSchema;

@Repository
public interface EventSchemaRepository extends ReactiveCrudRepository<EventSchema, Long> {
    Mono<Boolean> existsByEventTypeAndSchemaVersion(String eventType, String schemaVersion);
}
