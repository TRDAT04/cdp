package vn.vnpost.example.ingestion.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import vn.vnpost.example.common.response.MethodResult;

import vn.vnpost.example.ingestion.dto.ProfileIngestionRequest;
import vn.vnpost.example.ingestion.producer.ProfileIngestionProducer;
import vn.vnpost.shared.sercurity.CheckPermission;

@RestController
@RequestMapping("/api/v1/admin/profile-ingestion")
public class ProfileIngestionController {

    private final ProfileIngestionProducer profileIngestionProducer;

    public ProfileIngestionController(ProfileIngestionProducer profileIngestionProducer) {
        this.profileIngestionProducer = profileIngestionProducer;
    }

    @PostMapping("/send")
    @CheckPermission(index = 1, title = "Gửi Profile Ingestion")
    public Mono<ResponseEntity<MethodResult>> send(
            @Valid @RequestBody ProfileIngestionRequest request) {

        return Mono.fromCallable(() -> profileIngestionProducer.send(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> ResponseEntity.ok(
                        MethodResult.success(response)
                ));
    }
}
