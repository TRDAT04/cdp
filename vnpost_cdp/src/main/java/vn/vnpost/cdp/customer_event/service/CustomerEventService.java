package vn.vnpost.cdp.customer_event.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import vn.vnpost.cdp.customer_event.dto.CustomerEventDetailResponse;
import vn.vnpost.cdp.customer_event.dto.CustomerEventMessage;
import vn.vnpost.cdp.customer_event.dto.CustomerEventSearchRequest;

public interface CustomerEventService {

    void process(CustomerEventMessage message);

    Page<CustomerEventDetailResponse> searchEvents(
            CustomerEventSearchRequest request,
            Pageable pageable);
}