package vn.vnpost.cdp.unomi.service;

import reactor.core.publisher.Mono;
import vn.vnpost.cdp.profile.entity.MasterProfile;

public interface UnomiService {

    Mono<Object> syncProfileToUnomi(MasterProfile profile);
}
