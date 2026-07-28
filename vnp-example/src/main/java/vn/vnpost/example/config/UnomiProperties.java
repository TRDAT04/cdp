package vn.vnpost.example.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.unomi")
public class UnomiProperties {

    private String baseUrl = "http://localhost:8181";
    private String username = "karaf";
    private String password = "karaf";
    private String scope = "my-vnpost-app";
    private long connectionTimeoutMs = 5000;
    private long responseTimeoutMs = 15000;

}
