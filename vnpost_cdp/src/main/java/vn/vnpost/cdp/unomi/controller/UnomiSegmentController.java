package vn.vnpost.cdp.unomi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.unomi.service.UnomiSegmentService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/segments")
public class UnomiSegmentController {

    private final UnomiSegmentService segmentService;

    @GetMapping
    public ResponseEntity<MethodResult> getSegments() {
        return ResponseEntity.ok(MethodResult.success(segmentService.getSegments()));
    }

    @GetMapping("/{segmentId}")
    public ResponseEntity<MethodResult> getSegmentDetail(
            @PathVariable String segmentId) {
        return ResponseEntity.ok(MethodResult.success(segmentService.getSegmentDetail(segmentId)));
    }

    @GetMapping("/{segmentId}/members")
    public ResponseEntity<MethodResult> getSegmentMembers(
            @PathVariable String segmentId) {
        return ResponseEntity.ok(MethodResult.success(segmentService.getSegmentMembers(segmentId)));
    }
}
