package vn.vnpost.example.unomi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import vn.vnpost.example.unomi.client.UnomiClient;
import vn.vnpost.example.unomi.dto.UnomiProfileSearchResponse;
import vn.vnpost.example.unomi.dto.UnomiSegmentDetailResponse;
import vn.vnpost.example.unomi.dto.UnomiSegmentResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UnomiSegmentServiceImpl implements UnomiSegmentService {

    /** Giữ nguyên hành vi cũ của endpoint /members (trước đây hardcode limit=20). */
    private static final int DEFAULT_MEMBER_LIMIT = 20;

    private final UnomiClient unomiClient;

    @Override
    public Mono<List<UnomiSegmentResponse>> getSegments() {
        return unomiClient.getSegments();
    }

    @Override
    public Mono<UnomiSegmentDetailResponse> getSegmentDetail(String segmentId) {
        return unomiClient.getSegmentDetail(segmentId);
    }

    @Override
    public Mono<UnomiProfileSearchResponse> getSegmentMembers(String segmentId) {
        return unomiClient.getSegmentMembers(segmentId, DEFAULT_MEMBER_LIMIT);
    }
}
