package vn.vnpost.cdp.unomi.service;

import reactor.core.publisher.Mono;
import vn.vnpost.cdp.unomi.dto.UnomiProfileSearchResponse;
import vn.vnpost.cdp.unomi.dto.UnomiSegmentDetailResponse;
import vn.vnpost.cdp.unomi.dto.UnomiSegmentResponse;

import java.util.List;

public interface UnomiSegmentService {
    Mono<List<UnomiSegmentResponse>> getSegments();
    Mono<UnomiSegmentDetailResponse> getSegmentDetail(String segmentId);
    Mono<UnomiProfileSearchResponse> getSegmentMembers(String segmentId);
}
