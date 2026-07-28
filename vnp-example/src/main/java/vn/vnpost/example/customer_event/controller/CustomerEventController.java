package vn.vnpost.example.customer_event.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import vn.vnpost.example.common.response.MethodResult;
import vn.vnpost.example.customer_event.dto.CustomerEventRequest;
import vn.vnpost.example.customer_event.dto.CustomerEventSearchRequest;
import vn.vnpost.example.customer_event.producer.CustomerEventProducer;
import vn.vnpost.example.customer_event.service.CustomerEventService;

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
    public Mono<ResponseEntity<MethodResult>> send(@Valid @RequestBody CustomerEventRequest request) {
        log.info("POST /api/v1/customer-events/send - sourceSystem={}, sourceCustomerId={}, eventType={}",
                request.getSourceSystem(),
                request.getSourceCustomerId(),
                request.getEventType());

        // KafkaTemplate.send(...).get() bên trong producer là blocking — chạy trên
        // boundedElastic để không chiếm thread event-loop của Netty/WebFlux.
        return Mono.fromCallable(() -> customerEventProducer.send(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    @GetMapping
    public Mono<ResponseEntity<MethodResult>> searchEvents(
            @ModelAttribute CustomerEventSearchRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {

        log.info("GET /api/v1/customer-events - masterProfileId={}, eventType={}, timeRangeDays={}",
                request.getMasterProfileId(), request.getEventType(), request.getTimeRangeDays());

        Sort pageSort = StringUtils.hasText(sort)
                ? Sort.by(Sort.Direction.DESC, sort)
                : Sort.by(Sort.Direction.DESC, "occurredAt");
        Pageable pageable = PageRequest.of(page, size, pageSort);

        return customerEventService.searchEvents(request, pageable)
                .map(result -> ResponseEntity.ok(
                        MethodResult.success(result.getContent(), result.getTotalElements())));
    }
}
