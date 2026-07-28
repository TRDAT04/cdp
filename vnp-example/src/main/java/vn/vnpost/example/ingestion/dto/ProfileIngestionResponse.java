package vn.vnpost.example.ingestion.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileIngestionResponse {
    private String messageId;
    private String topic;
    private String sourceSystem;
    private String sourceCustomerId;
    private String eventType;
}
