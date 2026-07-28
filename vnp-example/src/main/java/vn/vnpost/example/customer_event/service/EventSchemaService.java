package vn.vnpost.example.customer_event.service;

import reactor.core.publisher.Mono;
import vn.vnpost.example.customer_event.dto.EventSchemaRequest;
import vn.vnpost.example.customer_event.dto.EventSchemaResponse;


public interface EventSchemaService {
    Mono<EventSchemaResponse> save(EventSchemaRequest schema);

    Mono<EventSchemaResponse> getSchemaById(Long id);
}
