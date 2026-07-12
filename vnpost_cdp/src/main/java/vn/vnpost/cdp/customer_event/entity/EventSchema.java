package vn.vnpost.cdp.customer_event.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Builder
@Getter
@Setter
@Table(name = "event_schemas")
@NoArgsConstructor
@AllArgsConstructor
public class EventSchema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String schemaVersion;

    private String eventType;

    private String sourceSystem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> jsonSchema;

    private String description;
}