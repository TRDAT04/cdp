package vn.vnpost.example.customer_event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerEventResponse {
    private String messageId;
    private String topic;
    private String sourceSystem;
    private String sourceCustomerId;
    private String eventType;
}
