package vn.vnpost.cdp.customer_event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.customer_event.dto.EventSchemaRequest;
import vn.vnpost.cdp.customer_event.dto.EventSchemaResponse;
import vn.vnpost.cdp.customer_event.service.EventSchemaService;


@RequiredArgsConstructor
@RequestMapping("/api/v1/eventSchemas")
@RestController
public class EventSchemaController {
    private final EventSchemaService eventSchemaService;

    @GetMapping("/{id}")
    public ResponseEntity<MethodResult> getSchema(@PathVariable Long id){
       return ResponseEntity.ok(MethodResult.success(eventSchemaService.getSchemaById(id)));
    }

    @PostMapping
    public ResponseEntity<MethodResult> createSchema(@RequestBody EventSchemaRequest request){
        return ResponseEntity.ok(MethodResult.success(eventSchemaService.save(request)));
    }
}
