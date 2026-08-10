package vn.vnpost.example.customer_event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import vn.vnpost.example.common.response.MethodResult;

import vn.vnpost.example.customer_event.dto.EventSchemaRequest;
import vn.vnpost.example.customer_event.service.EventSchemaService;
import vn.vnpost.shared.sercurity.CheckPermission;

@RequiredArgsConstructor
@RequestMapping("/api/v1/eventSchemas")
@RestController
public class EventSchemaController {

    private final EventSchemaService eventSchemaService;

    @GetMapping("/{id}")
    @CheckPermission(index = 1, title = "Xem Event Schema")
    public Mono<ResponseEntity<MethodResult>> getSchema(@PathVariable Long id) {
        return eventSchemaService.getSchemaById(id)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }

    @PostMapping
    @CheckPermission(index = 2, title = "Tạo Event Schema")
    public Mono<ResponseEntity<MethodResult>> createSchema(
            @RequestBody EventSchemaRequest request) {
        return eventSchemaService.save(request)
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }
}

