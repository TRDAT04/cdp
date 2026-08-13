package vn.vnpost.cdp.common.security;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PermissionClient {

    @Value("${core.dsAPI}")
    String endPoint;

    @Value("${core.validToken}")
    String validToken;

    @Value("${core.baseUrl}")
    String coreBaseUrl;

    final WebClient permissionWebClient;

    public PermissionClient(@Qualifier("coreClient") WebClient permissionWebClient) {
        this.permissionWebClient = permissionWebClient;
    }

    /**
     * Gọi Core API /users/ValidateToken để kiểm tra token còn hiệu lực thật hay không
     * (JWT còn hạn nhưng có thể đã bị thu hồi/logout sớm bên Core — điều mà việc verify
     * chữ ký + exp cục bộ của Keycloak JWT không biết được).
     */
    public Mono<Boolean> validateToken(String token) {
        return permissionWebClient.post()
                .uri(validToken)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    log.error("ValidateToken API trả lỗi status={}", clientResponse.statusCode());
                    return clientResponse.createException();
                })
                .bodyToMono(PermissionResponse.class)
                .map(PermissionResponse::isSuccess)
                .defaultIfEmpty(false);
    }

    @SuppressWarnings("unchecked")
    public Mono<List<String>> getPermissions(String token) {
        log.info("Calling permission API: {}", coreBaseUrl + endPoint);

        return permissionWebClient.get()
                .uri(endPoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    log.error("Permission API trả lỗi status={}", clientResponse.statusCode());
                    return clientResponse.createException();
                })
                .bodyToMono(PermissionResponse.class)
                .map(response -> {
                    if (response != null && response.isSuccess() && response.getData() != null) {
                        return (List<String>) response.getData();
                    }

                    return List.<String>of();
                })
                .defaultIfEmpty(List.of());
    }
}