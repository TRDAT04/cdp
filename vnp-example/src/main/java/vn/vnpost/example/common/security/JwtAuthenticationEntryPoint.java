package vn.vnpost.example.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import vn.vnpost.shared.models.MethodResult;

import java.nio.charset.StandardCharsets;

/**
 * Chuẩn hóa response khi request không có token / token hết hạn / token không hợp lệ.
 * Không dùng — Spring Security mặc định trả 401 với body rỗng (hoặc header WWW-Authenticate),
 * không đúng format MethodResult chung của API và dễ bị FE hiểu nhầm thành lỗi hệ thống.
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        String message = resolveMessage(ex);
        log.warn("Authentication failed: {} - {}", exchange.getRequest().getPath(), ex.getMessage());

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(MethodResult.error(message));
        } catch (Exception e) {
            body = ("{\"data\":null,\"message\":\"" + message
                    + "\",\"status\":false,\"success\":false,\"totalRecord\":null}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String resolveMessage(AuthenticationException ex) {
        String description = null;
        if (ex instanceof OAuth2AuthenticationException oauthEx && oauthEx.getError() != null) {
            description = oauthEx.getError().getDescription();
        }

        if (description != null && description.toLowerCase().contains("expired")) {
            return "Token đã hết hạn, vui lòng đăng nhập lại";
        }

        return "Token không hợp lệ hoặc đã hết hạn, vui lòng đăng nhập lại";
    }
}
