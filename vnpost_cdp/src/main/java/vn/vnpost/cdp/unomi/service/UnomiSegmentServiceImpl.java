package vn.vnpost.cdp.unomi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vnpost.cdp.unomi.client.UnomiClient;
import vn.vnpost.cdp.unomi.dto.UnomiSegmentDetailResponse;
import vn.vnpost.cdp.unomi.dto.UnomiSegmentResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UnomiSegmentServiceImpl implements UnomiSegmentService {

    /** Giữ nguyên hành vi cũ của endpoint /members (trước đây hardcode limit=20). */
    private static final int DEFAULT_MEMBER_LIMIT = 20;

    private final UnomiClient unomiClient;

    @Override
    public List<UnomiSegmentResponse> getSegments() {
        return unomiClient.getSegments().block();
    }
    @Override
    public UnomiSegmentDetailResponse getSegmentDetail(String segmentId) {
        return unomiClient.getSegmentDetail(segmentId).block();
    }
    @Override
    public Object getSegmentMembers(String segmentId) {
        return unomiClient.getSegmentMembers(segmentId, DEFAULT_MEMBER_LIMIT).block();
    }
}