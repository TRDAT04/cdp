package vn.vnpost.cdp.common.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.util.Optional;

public class AuthUtils {

    public static Mono<Boolean> isSuperUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .cast(Jwt.class)
                .map(jwt -> {
                    String val = Optional.ofNullable(jwt.getClaims().get("issuperuser"))
                            .map(Object::toString)
                            .orElse("false");

                    return Boolean.parseBoolean(val);
                })
                .defaultIfEmpty(false);
    }

    public static Mono<Jwt> getJwt() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .cast(Jwt.class);
    }

    /**
     * Lấy organizationName từ JWT claim, an toàn khi chưa authenticated hoặc token
     * không có claim này — trả về {@code Optional.empty()}, không bao giờ lỗi/rỗng bất thường.
     */
    public static Mono<Optional<String>> getOrganizationName() {
        return getJwt()
                .map(jwt -> Optional.ofNullable(jwt.getClaimAsString("organizationName")))
                .defaultIfEmpty(Optional.empty());
    }
}
