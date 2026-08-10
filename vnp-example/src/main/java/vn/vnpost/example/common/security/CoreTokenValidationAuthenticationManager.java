package vn.vnpost.example.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Bọc thêm 1 bước sau khi JWT đã được verify chữ ký + hạn (qua {@link JwtReactiveAuthenticationManager}):
 * gọi Core API /users/ValidateToken để chắc chắn token chưa bị thu hồi/logout sớm.
 * Áp dụng cho MỌI request đã authenticated — xem SecurityConfig.
 */
@Slf4j
@Component
public class CoreTokenValidationAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtReactiveAuthenticationManager delegate;
    private final PermissionClient permissionClient;

    public CoreTokenValidationAuthenticationManager(ReactiveJwtDecoder jwtDecoder, PermissionClient permissionClient) {
        this.delegate = new JwtReactiveAuthenticationManager(jwtDecoder);
        this.permissionClient = permissionClient;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return delegate.authenticate(authentication)
                .flatMap(authResult -> {
                    String token = ((BearerTokenAuthenticationToken) authentication).getToken();

                    return permissionClient.validateToken(token)
                            .onErrorResume(e -> {
                                log.error("Lỗi gọi Core ValidateToken: {}", e.getMessage(), e);
                                return Mono.just(false);
                            })
                            .flatMap(valid -> Boolean.TRUE.equals(valid)
                                    ? Mono.just(authResult)
                                    : Mono.error(new InvalidBearerTokenException(
                                            "Token đã bị thu hồi hoặc không còn hiệu lực, vui lòng đăng nhập lại"))
                            );
                });
    }
}
