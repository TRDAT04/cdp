package vn.vnpost.cdp.unomi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vnpost.cdp.unomi.dto.UnomiSegmentDetailResponse;
import vn.vnpost.cdp.unomi.dto.UnomiSegmentResponse;
import vn.vnpost.cdp.unomi.service.UnomiSegmentService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/segments")
public class UnomiSegmentController {

    private final UnomiSegmentService segmentService;

    @GetMapping
    public ResponseEntity<List<UnomiSegmentResponse>> getSegments() {
        return ResponseEntity.ok(segmentService.getSegments());
    }

    @GetMapping("/{segmentId}")
    public ResponseEntity<UnomiSegmentDetailResponse> getSegmentDetail(
            @PathVariable String segmentId) {
        return ResponseEntity.ok(segmentService.getSegmentDetail(segmentId));
    }

    @GetMapping("/{segmentId}/members")
    public ResponseEntity<Object> getSegmentMembers(
            @PathVariable String segmentId) {
        return ResponseEntity.ok(segmentService.getSegmentMembers(segmentId));
    }
}
