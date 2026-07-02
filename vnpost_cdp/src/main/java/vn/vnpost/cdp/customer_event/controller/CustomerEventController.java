package vn.vnpost.cdp.customer_event.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.customer_event.dto.CustomerEventRequest;
import vn.vnpost.cdp.customer_event.dto.CustomerEventResponse;
import vn.vnpost.cdp.customer_event.producer.CustomerEventProducer;

@Slf4j
@RestController
@RequestMapping("/v1/customer-events")
public class CustomerEventController {

    private final CustomerEventProducer customerEventProducer;

    public CustomerEventController(CustomerEventProducer customerEventProducer) {
        this.customerEventProducer = customerEventProducer;
    }

    @PostMapping("/send")
    public ResponseEntity<MethodResult> send(@Valid @RequestBody CustomerEventRequest request) {
        log.info("POST /v1/customer-events/send - sourceSystem={}, sourceCustomerId={}, eventType={}",
                request.getSourceSystem(),request.getSourceCustomerId(),request.getEventType());
        CustomerEventResponse response = customerEventProducer.send(request);
        return ResponseEntity.ok(MethodResult.success(response));
    }
}