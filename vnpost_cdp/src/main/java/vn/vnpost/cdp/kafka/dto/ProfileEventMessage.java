package vn.vnpost.cdp.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ProfileEventMessage {

    private String sourceSystem;
    private String sourceCustomerId;
    private String eventType;
    private String profileCode;
    private Map<String, Object> payload;
    private LocalDateTime occurredAt;
}
