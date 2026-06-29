package vn.vnpost.cdp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class SecurityProperties {

    private String clientId = "vnpost-cdp";
    private String authorityPrefix = "ROLE_";
    private String scopePrefix = "SCOPE_";
}
