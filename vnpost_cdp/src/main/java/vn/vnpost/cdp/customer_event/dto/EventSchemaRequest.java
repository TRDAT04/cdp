package vn.vnpost.cdp.customer_event.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Data
public class EventSchemaRequest {
    private String eventType;
    private String schemaVersion;
    private String sourceSystem;
    private String description;
    private List<EventFieldRequest> fields;

}
