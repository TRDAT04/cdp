package vn.vnpost.cdp.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
public class ProfileIngestionRequest {

    @NotBlank(message = "sourceSystem must not be blank")
    private String sourceSystem;

    @NotBlank(message = "sourceCustomerId must not be blank")
    private String sourceCustomerId;

    @NotBlank(message = "eventType must not be blank")
    private String eventType;

    @NotNull(message = "payload must not be null")
    private Map<String, Object> payload;

    private LocalDateTime occurredAt;
}
