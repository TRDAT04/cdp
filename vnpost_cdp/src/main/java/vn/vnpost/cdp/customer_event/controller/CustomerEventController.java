package vn.vnpost.cdp.customer_event.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.customer_event.dto.CustomerEventDetailResponse;
import vn.vnpost.cdp.customer_event.dto.CustomerEventRequest;
import vn.vnpost.cdp.customer_event.dto.CustomerEventResponse;
import vn.vnpost.cdp.customer_event.dto.CustomerEventSearchRequest;
import vn.vnpost.cdp.customer_event.producer.CustomerEventProducer;
import vn.vnpost.cdp.customer_event.service.CustomerEventService;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/customer-events")
public class CustomerEventController {

    private final CustomerEventProducer customerEventProducer;
    private final CustomerEventService customerEventService;

    public CustomerEventController(CustomerEventProducer customerEventProducer,
                                   CustomerEventService customerEventService) {
        this.customerEventProducer = customerEventProducer;
        this.customerEventService = customerEventService;
    }

    @PostMapping("/send")
    public ResponseEntity<MethodResult> send(@Valid @RequestBody CustomerEventRequest request) {
        log.info("POST /api/v1/customer-events/send - sourceSystem={}, sourceCustomerId={}, eventType={}",
                request.getSourceSystem(),
                request.getSourceCustomerId(),
                request.getEventType());

        CustomerEventResponse response = customerEventProducer.send(request);
        return ResponseEntity.ok(MethodResult.success(response));
    }

    @GetMapping
    public ResponseEntity<MethodResult> searchEvents(
            @ModelAttribute CustomerEventSearchRequest request,
            @PageableDefault(sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("GET /api/v1/customer-events - masterProfileId={}, eventType={}, timeRangeDays={}",
                request.getMasterProfileId(), request.getEventType(), request.getTimeRangeDays());

        Page<CustomerEventDetailResponse> result = customerEventService.searchEvents(request, pageable);

        return ResponseEntity.ok(MethodResult.success(result.getContent(), result.getTotalElements()));
    }
}