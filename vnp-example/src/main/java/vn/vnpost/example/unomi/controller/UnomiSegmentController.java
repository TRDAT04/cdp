package vn.vnpost.example.unomi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import vn.vnpost.example.common.response.MethodResult;
import vn.vnpost.example.unomi.service.UnomiSegmentService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/segments")
public class UnomiSegmentController {

    private final UnomiSegmentService segmentService;

    @GetMapping
    public Mono<ResponseEntity<MethodResult>> getSegments() {
        return segmentService.getSegments()
                .map(segments -> ResponseEntity.ok(MethodResult.success(segments)));
    }

    @GetMapping("/{segmentId}")
    public Mono<ResponseEntity<MethodResult>> getSegmentDetail(
            @PathVariable String segmentId) {
        return segmentService.getSegmentDetail(segmentId)
                .map(detail -> ResponseEntity.ok(MethodResult.success(detail)));
    }

    @GetMapping("/{segmentId}/members")
    public Mono<ResponseEntity<MethodResult>> getSegmentMembers(
            @PathVariable String segmentId) {
        return segmentService.getSegmentMembers(segmentId)
                .map(members -> ResponseEntity.ok(MethodResult.success(members)));
    }
}
