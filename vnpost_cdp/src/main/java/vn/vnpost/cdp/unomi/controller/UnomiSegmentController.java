package vn.vnpost.cdp.unomi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.common.response.MethodResult;

import vn.vnpost.cdp.unomi.service.UnomiSegmentService;
import vn.vnpost.shared.sercurity.CheckPermission;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/segments")
public class UnomiSegmentController {

    private final UnomiSegmentService segmentService;

    @GetMapping
    @CheckPermission(index = 1, title = "Xem danh sách Segment")
    public Mono<ResponseEntity<MethodResult>> getSegments() {
        return segmentService.getSegments()
                .map(segments -> ResponseEntity.ok(MethodResult.success(segments)));
    }

    @GetMapping("/{segmentId}")
    @CheckPermission(index = 2, title = "Xem chi tiết Segment")
    public Mono<ResponseEntity<MethodResult>> getSegmentDetail(
            @PathVariable String segmentId) {
        return segmentService.getSegmentDetail(segmentId)
                .map(detail -> ResponseEntity.ok(MethodResult.success(detail)));
    }

    @GetMapping("/{segmentId}/members")
    @CheckPermission(index = 3, title = "Xem thành viên Segment")
    public Mono<ResponseEntity<MethodResult>> getSegmentMembers(
            @PathVariable String segmentId) {
        return segmentService.getSegmentMembers(segmentId)
                .map(members -> ResponseEntity.ok(MethodResult.success(members)));
    }
}

