package vn.vnpost.example.ingestion.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import vn.vnpost.example.common.response.MethodResult;
import vn.vnpost.example.ingestion.dto.ProfileIngestionRequest;
import vn.vnpost.example.ingestion.producer.ProfileIngestionProducer;

@Slf4j
@RestController
@RequestMapping("/api/v1/test/profile-ingestion")
public class ProfileIngestionController {

    private final ProfileIngestionProducer profileIngestionProducer;

    public ProfileIngestionController(ProfileIngestionProducer profileIngestionProducer) {
        this.profileIngestionProducer = profileIngestionProducer;
    }

    @PostMapping("/send")
    public Mono<ResponseEntity<MethodResult>> send(@Valid @RequestBody ProfileIngestionRequest request) {
        log.info("POST /v1/test/profile-ingestion/send - sourceSystem={}, sourceCustomerId={}",
                request.getSourceSystem(), request.getSourceCustomerId());
        // KafkaTemplate.send(...).get() bên trong producer là blocking — chạy trên
        // boundedElastic để không chiếm thread event-loop của Netty/WebFlux.
        return Mono.fromCallable(() -> profileIngestionProducer.send(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> ResponseEntity.ok(MethodResult.success(response)));
    }


}
