package vn.vnpost.cdp.customer_event.producer;

import vn.vnpost.cdp.customer_event.dto.CustomerEventRequest;
import vn.vnpost.cdp.customer_event.dto.CustomerEventResponse;

public interface CustomerEventProducer {

    CustomerEventResponse send(CustomerEventRequest request);

}