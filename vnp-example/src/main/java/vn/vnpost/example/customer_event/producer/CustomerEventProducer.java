package vn.vnpost.example.customer_event.producer;

import vn.vnpost.example.customer_event.dto.CustomerEventRequest;
import vn.vnpost.example.customer_event.dto.CustomerEventResponse;

public interface CustomerEventProducer {

    CustomerEventResponse send(CustomerEventRequest request);

}
