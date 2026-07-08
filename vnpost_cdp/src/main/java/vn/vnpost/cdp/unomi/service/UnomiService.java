package vn.vnpost.cdp.unomi.service;

import reactor.core.publisher.Mono;
import vn.vnpost.cdp.profile.entity.MasterProfile;
import vn.vnpost.cdp.unomi.dto.UnomiEventRequest;
import vn.vnpost.cdp.unomi.dto.UnomiProfileResponse;
import vn.vnpost.cdp.unomi.dto.UnomiProfileSearchResponse;

public interface UnomiService {

    Mono<Object> syncProfileToUnomi(MasterProfile profile);
    Mono<Object> sendEventToUnomi(UnomiEventRequest request);
    Mono<UnomiProfileSearchResponse> getProfiles(Integer offset, Integer limit);
    Mono<UnomiProfileResponse> getProfileByItemId(String itemId);
}
