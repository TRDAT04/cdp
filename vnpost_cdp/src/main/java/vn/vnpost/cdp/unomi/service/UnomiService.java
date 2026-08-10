package vn.vnpost.cdp.unomi.service;

import reactor.core.publisher.Mono;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.unomi.dto.UnomiEventRequest;

public interface UnomiService {

    Mono<Object> syncProfileToUnomi(MasterProfile profile);

    Mono<Object> sendEventToUnomi(UnomiEventRequest request);
}
