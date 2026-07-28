package vn.vnpost.example.unomi.service;

import reactor.core.publisher.Mono;
import vn.vnpost.example.profile.entity.MasterProfile;
import vn.vnpost.example.unomi.dto.UnomiEventRequest;

public interface UnomiService {

    Mono<Object> syncProfileToUnomi(MasterProfile profile);

    Mono<Object> sendEventToUnomi(UnomiEventRequest request);
}
