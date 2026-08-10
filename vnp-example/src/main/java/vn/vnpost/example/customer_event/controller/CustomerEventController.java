package vn.vnpost.example.customer_event.controller;

import jakarta.validation.Valid;
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
import vn.vnpost.shared.sercurity.CheckPermission;

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
    @CheckPermission(index = 1, title = "Gửi Customer Event")
    public Mono<ResponseEntity<MethodResult>> send(
            @Valid @RequestBody CustomerEventRequest request) {

        return Mono.fromCallable(() -> customerEventProducer.send(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> ResponseEntity.ok(
                        MethodResult.success(response)
                ));
    }

    @GetMapping
    @CheckPermission(index = 2, title = "Xem Customer Event")
    public Mono<ResponseEntity<MethodResult>> searchEvents(
            @ModelAttribute CustomerEventSearchRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {

        Sort pageSort = StringUtils.hasText(sort)
                ? Sort.by(Sort.Direction.DESC, sort)
                : Sort.by(Sort.Direction.DESC, "occurredAt");

        Pageable pageable = PageRequest.of(page, size, pageSort);

        return customerEventService.searchEvents(request, pageable)
                .map(result -> ResponseEntity.ok(
                        MethodResult.success(
                                result.getContent(),
                                result.getTotalElements()
                        )
                ));
    }
}
