package vn.vnpost.cdp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
        // Utility class
    }

    public static Optional<String> getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return Optional.of(preferredUsername);
            }
            String username = jwt.getClaimAsString("username");
            if (username != null && !username.isBlank()) {
                return Optional.of(username);
            }
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                return Optional.of(sub);
            }
        }

        if (principal instanceof UserDetails userDetails) {
            return Optional.of(userDetails.getUsername());
        }

        if (principal instanceof String name && !name.isBlank()
                && !"anonymousUser".equals(name)) {
            return Optional.of(name);
        }

        return Optional.empty();
    }
}
