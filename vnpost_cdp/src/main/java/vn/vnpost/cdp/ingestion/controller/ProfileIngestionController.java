package vn.vnpost.cdp.ingestion.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.ingestion.dto.ProfileIngestionRequest;
import vn.vnpost.cdp.ingestion.dto.ProfileIngestionResponse;
import vn.vnpost.cdp.ingestion.producer.ProfileIngestionProducer;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/test/profile-ingestion")
public class ProfileIngestionController {

    private final ProfileIngestionProducer profileIngestionProducer;

    public ProfileIngestionController(ProfileIngestionProducer profileIngestionProducer) {
        this.profileIngestionProducer = profileIngestionProducer;
    }

    @PostMapping("/send")
    public ResponseEntity<MethodResult> send(@Valid @RequestBody ProfileIngestionRequest request) {
        log.info("POST /v1/test/profile-ingestion/send - sourceSystem={}, sourceCustomerId={}",
                request.getSourceSystem(), request.getSourceCustomerId());
        ProfileIngestionResponse response = profileIngestionProducer.send(request);
        return ResponseEntity.ok(MethodResult.success(response));
    }


}
