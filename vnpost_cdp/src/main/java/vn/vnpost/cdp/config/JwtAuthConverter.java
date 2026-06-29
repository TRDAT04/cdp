package vn.vnpost.cdp.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final SecurityProperties securityProperties;
    private final JwtGrantedAuthoritiesConverter defaultGrantedAuthoritiesConverter =
            new JwtGrantedAuthoritiesConverter();

    public JwtAuthConverter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                defaultGrantedAuthoritiesConverter.convert(jwt).stream(),
                extractAuthorities(jwt).stream()
        ).collect(Collectors.toSet());

        String username = extractUsername(jwt);
        return new JwtAuthenticationToken(jwt, authorities, username);
    }

    private String extractUsername(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }
        String username = jwt.getClaimAsString("username");
        if (username != null && !username.isBlank()) {
            return username;
        }
        return jwt.getSubject();
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            Object roles = realmAccess.get("roles");
            if (roles instanceof Collection<?> roleList) {
                roleList.stream()
                        .map(role -> new SimpleGrantedAuthority(
                                securityProperties.getAuthorityPrefix() + role))
                        .forEach(authorities::add);
            }
        }

        // resource_access.{clientId}.roles
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            Object clientAccess = resourceAccess.get(securityProperties.getClientId());
            if (clientAccess instanceof Map<?, ?> clientMap) {
                Object roles = clientMap.get("roles");
                if (roles instanceof Collection<?> roleList) {
                    roleList.stream()
                            .map(role -> new SimpleGrantedAuthority(
                                    securityProperties.getAuthorityPrefix() + role))
                            .forEach(authorities::add);
                }
            }
        }

        // scope
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            Arrays.stream(scope.split(" "))
                    .filter(s -> !s.isBlank())
                    .map(s -> new SimpleGrantedAuthority(securityProperties.getScopePrefix() + s))
                    .forEach(authorities::add);
        }

        // scp (alternative scope claim)
        Object scp = jwt.getClaim("scp");
        if (scp instanceof Collection<?> scpList) {
            scpList.stream()
                    .map(s -> new SimpleGrantedAuthority(securityProperties.getScopePrefix() + s))
                    .forEach(authorities::add);
        } else if (scp instanceof String scpStr && !scpStr.isBlank()) {
            Arrays.stream(scpStr.split(" "))
                    .filter(s -> !s.isBlank())
                    .map(s -> new SimpleGrantedAuthority(securityProperties.getScopePrefix() + s))
                    .forEach(authorities::add);
        }

        return authorities;
    }
}
