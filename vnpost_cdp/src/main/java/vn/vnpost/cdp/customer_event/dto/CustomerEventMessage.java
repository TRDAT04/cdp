package vn.vnpost.cdp.customer_event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerEventMessage {
    private String messageId;
    private String sourceSystem;
    private String sourceCustomerId;
    private String eventType;
    private String sessionId;
    private Map<String, Object> source;
    private Map<String, Object> target;
    private Map<String, Object> properties;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
}