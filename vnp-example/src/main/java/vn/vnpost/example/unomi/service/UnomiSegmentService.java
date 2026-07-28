package vn.vnpost.example.unomi.service;

import reactor.core.publisher.Mono;
import vn.vnpost.example.unomi.dto.UnomiProfileSearchResponse;
import vn.vnpost.example.unomi.dto.UnomiSegmentDetailResponse;
import vn.vnpost.example.unomi.dto.UnomiSegmentResponse;

import java.util.List;

public interface UnomiSegmentService {
    Mono<List<UnomiSegmentResponse>> getSegments();
    Mono<UnomiSegmentDetailResponse> getSegmentDetail(String segmentId);
    Mono<UnomiProfileSearchResponse> getSegmentMembers(String segmentId);
}
