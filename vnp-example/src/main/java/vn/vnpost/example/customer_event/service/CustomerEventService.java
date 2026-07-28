package vn.vnpost.example.customer_event.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

import vn.vnpost.example.customer_event.dto.CustomerEventDetailResponse;
import vn.vnpost.example.customer_event.dto.CustomerEventMessage;
import vn.vnpost.example.customer_event.dto.CustomerEventSearchRequest;

public interface CustomerEventService {

    Mono<Void> process(CustomerEventMessage message);

    Mono<Page<CustomerEventDetailResponse>> searchEvents(
            CustomerEventSearchRequest request,
            Pageable pageable);
}
