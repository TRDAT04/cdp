package vn.vnpost.cdp.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Tách biệt hai concerns:
 * - Fetch JWKS public keys: dùng URL nội bộ Docker ({@code jwk-set-uri}) để tránh timeout loopback.
 * - Validate claim {@code iss}: so sánh với địa chỉ external ({@code issuer-uri}) đúng như token được cấp.
 */
@Configuration
public class JwtDecoderConfig {

    /** Địa chỉ external mà Identity Server ghi vào claim "iss" của JWT. */
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    /** URL nội bộ (Docker network) để fetch JWKS public keys. */
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .build();

        // Validate claim "iss" khớp với issuer external + validate thời hạn token
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuerUri);
        decoder.setJwtValidator(validator);

        return decoder;
    }
}
