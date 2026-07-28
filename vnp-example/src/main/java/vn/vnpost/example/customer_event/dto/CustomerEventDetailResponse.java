package vn.vnpost.example.customer_event.dto;

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
public class CustomerEventDetailResponse {
    private Long id;
    private String eventCode;
    private Long masterProfileId;
    private String profileCode;
    private String eventType;
    private String sessionId;
    private String sourceSystem;
    private String sourceCustomerId;
    private LocalDateTime occurredAt;
    private Map<String, Object> properties;
    private Map<String, Object> source;
    private Map<String, Object> target;
    private Short syncStatus;
    private LocalDateTime syncedToUnomiAt;
}
