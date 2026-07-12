package vn.vnpost.cdp.customer_event.service;

import vn.vnpost.cdp.customer_event.dto.EventSchemaRequest;
import vn.vnpost.cdp.customer_event.dto.EventSchemaResponse;
import vn.vnpost.cdp.customer_event.entity.EventSchema;


public interface EventSchemaService {
    EventSchemaResponse save(EventSchemaRequest schema);

    EventSchemaResponse getSchemaById(Long id);
}
