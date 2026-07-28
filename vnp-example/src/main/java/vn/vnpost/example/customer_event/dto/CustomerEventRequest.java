package vn.vnpost.example.customer_event.dto;

import jakarta.validation.constraints.NotBlank;
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
public class CustomerEventRequest {
    @NotBlank
    private String sourceSystem;
    @NotBlank
    private String sourceCustomerId;
    @NotBlank
    private String eventType;
    private String sessionId;
    private LocalDateTime occurredAt;
    private Map<String, Object> properties;

    private Map<String, Object> source;

    private Map<String, Object> target;
}
