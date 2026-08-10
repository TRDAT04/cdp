package vn.vnpost.example.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

/**
 * Bản reactive của {@code SecurityUtils} gốc (vnpost_cdp). Trong WebFlux, security context
 * nằm trong Reactor Context (qua {@link ReactiveSecurityContextHolder}), KHÔNG phải ThreadLocal
 * ({@code SecurityContextHolder} kiểu Servlet) — port y nguyên bản blocking sẽ luôn trả rỗng
 * một cách âm thầm vì ThreadLocal không được set trong luồng WebFlux.
 *
 * <p>{@link #getCurrentUsername()} trả {@code Mono<String>} rỗng nếu chưa authenticate;
 * {@link #getCurrentUsernameOrSystem()} là tiện ích tương đương {@code orElse("system")}
 * của bản gốc, dùng trực tiếp trong chuỗi {@code flatMap}.</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Utility class
    }

    public static Mono<String> getCurrentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(ctx -> ctx.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .mapNotNull(Authentication::getPrincipal)
                .mapNotNull(SecurityUtils::extractUsername);
    }

    /** Tương đương {@code getCurrentUsername().orElse("system")} của bản blocking gốc. */
    public static Mono<String> getCurrentUsernameOrSystem() {
        return getCurrentUsername().defaultIfEmpty("system");
    }

    private static String extractUsername(Object principal) {
        if (principal instanceof Jwt jwt) {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
            String username = jwt.getClaimAsString("username");
            if (username != null && !username.isBlank()) {
                return username;
            }
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                return sub;
            }
            return null;
        }

        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        if (principal instanceof String name && !name.isBlank() && !"anonymousUser".equals(name)) {
            return name;
        }

        return null;
    }
}
