package vn.vnpost.example.customer_event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSchemaResponse {

    private Long id;

    private String schemaCode;

    private String schemaVersion;

    private String eventType;

    private String sourceSystem;

    private String description;

    private Boolean isActive;

    private Short status;

    private Map<String, Object> jsonSchema;

    private LocalDateTime created;
}
