package vn.vnpost.cdp.customer_event.service;

import vn.vnpost.cdp.customer_event.dto.CustomerEventMessage;

public interface CustomerEventService {

    void process(CustomerEventMessage message);

}
