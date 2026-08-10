package vn.vnpost.cdp.customer_event.service;

import reactor.core.publisher.Mono;
import vn.vnpost.cdp.customer_event.dto.EventSchemaRequest;
import vn.vnpost.cdp.customer_event.dto.EventSchemaResponse;


public interface EventSchemaService {
    Mono<EventSchemaResponse> save(EventSchemaRequest schema);

    Mono<EventSchemaResponse> getSchemaById(Long id);
}
