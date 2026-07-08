package vn.vnpost.cdp.unomi.service;

import vn.vnpost.cdp.unomi.dto.UnomiSegmentDetailResponse;
import vn.vnpost.cdp.unomi.dto.UnomiSegmentResponse;

import java.util.List;

public interface UnomiSegmentService {
    List<UnomiSegmentResponse> getSegments();
    UnomiSegmentDetailResponse getSegmentDetail(String segmentId);
    Object getSegmentMembers(String segmentId);
}
