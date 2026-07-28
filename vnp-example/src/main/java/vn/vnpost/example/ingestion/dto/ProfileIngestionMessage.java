package vn.vnpost.example.ingestion.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileIngestionMessage {

    private String messageId;
    private String sourceSystem;
    private String sourceCustomerId;
    private String eventType;
    private Map<String, Object> payload;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
}
