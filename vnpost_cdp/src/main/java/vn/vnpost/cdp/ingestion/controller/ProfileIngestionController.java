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
@RequestMapping("/v1/test/profile-ingestion")
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

    @PostMapping("/send-crm-sample")
    public ResponseEntity<MethodResult> sendCrmSample() {
        log.info("POST /v1/test/profile-ingestion/send-crm-sample");
        ProfileIngestionRequest request = buildCrmSample();
        ProfileIngestionResponse response = profileIngestionProducer.send(request);
        return ResponseEntity.ok(MethodResult.success(response));
    }

    @PostMapping("/send-cms-sample")
    public ResponseEntity<MethodResult> sendCmsSample() {
        log.info("POST /v1/test/profile-ingestion/send-cms-sample");
        ProfileIngestionRequest request = buildCmsSample();
        ProfileIngestionResponse response = profileIngestionProducer.send(request);
        return ResponseEntity.ok(MethodResult.success(response));
    }

    private ProfileIngestionRequest buildCrmSample() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fullName", "Nguyen Van A");
        payload.put("phone", "0988888888");
        payload.put("email", "a@gmail.com");
        payload.put("identityNo", "0123456789");
        payload.put("gender", "male");
        payload.put("customerType", "PERSONAL");
        ProfileIngestionRequest req = new ProfileIngestionRequest();
        req.setSourceSystem("CRM");
        req.setSourceCustomerId("CRM_SAMPLE_001");
        req.setEventType("PROFILE_UPDATED");
        req.setPayload(payload);
        req.setOccurredAt(LocalDateTime.now());
        return req;
    }

    private ProfileIngestionRequest buildCmsSample() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fullName", "Nguyen Van A");
        payload.put("phone", "0988888888");
        payload.put("email", "a@gmail.com");
        payload.put("interestedServices", java.util.List.of("EMS", "Logistics"));
        payload.put("lastVisitAt", LocalDateTime.now().toString());
        ProfileIngestionRequest req = new ProfileIngestionRequest();
        req.setSourceSystem("CMS");
        req.setSourceCustomerId("CMS_SAMPLE_001");
        req.setEventType("PROFILE_UPDATED");
        req.setPayload(payload);
        req.setOccurredAt(LocalDateTime.now());
        return req;
    }
}
