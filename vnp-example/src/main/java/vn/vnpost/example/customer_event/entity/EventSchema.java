package vn.vnpost.example.customer_event.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.Map;

@Table("event_schemas")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventSchema {

    @Id
    @Column("id")
    private Long id;

    @Column("schema_version")
    private String schemaVersion;

    @Column("event_type")
    private String eventType;

    @Column("source_system")
    private String sourceSystem;

    @Column("json_schema")
    private Map<String, Object> jsonSchema;

    @Column("description")
    private String description;
}
