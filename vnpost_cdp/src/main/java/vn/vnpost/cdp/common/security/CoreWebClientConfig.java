package vn.vnpost.cdp.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class CoreWebClientConfig {

    /** Base URL nội bộ (Docker network) để gọi Core API. */
    @Value("${core.baseUrl}")
    private String coreBaseUrl;

    @Bean("coreClient")
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(coreBaseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}