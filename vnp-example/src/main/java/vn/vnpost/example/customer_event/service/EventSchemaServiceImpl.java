package vn.vnpost.example.customer_event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import vn.vnpost.example.common.exception.BusinessException;
import vn.vnpost.example.customer_event.dto.EventFieldRequest;
import vn.vnpost.example.customer_event.dto.EventSchemaRequest;
import vn.vnpost.example.customer_event.dto.EventSchemaResponse;
import vn.vnpost.example.customer_event.entity.EventSchema;
import vn.vnpost.example.customer_event.repository.EventSchemaRepository;
import vn.vnpost.example.unomi.client.UnomiClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EventSchemaServiceImpl implements EventSchemaService {

    private static final String JSON_SCHEMA_DRAFT = "https://json-schema.org/draft/2019-09/schema";
    private static final String UNOMI_EVENT_SCHEMA_REF = "https://unomi.apache.org/schemas/json/event/1-0-0";
    private static final String UNOMI_ITEM_SCHEMA_REF = "https://unomi.apache.org/schemas/json/item/1-0-0";
    private static final String SCHEMA_BASE_URI = "https://demo.local/schemas/json/events/";
    private static final String VENDOR = "vn.vnpost.example";

    private final EventSchemaRepository eventSchemaRepository;
    private final UnomiClient unomiClient;

    @Override
    public Mono<EventSchemaResponse> save(EventSchemaRequest request) {
        return eventSchemaRepository.existsByEventTypeAndSchemaVersion(
                        request.getEventType(), request.getSchemaVersion())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new BusinessException("Schema already exists"));
                    }

                    EventSchema schema = EventSchema.builder()
                            .schemaVersion(request.getSchemaVersion())
                            .eventType(request.getEventType())
                            .sourceSystem(request.getSourceSystem())
                            .description(request.getDescription())
                            .jsonSchema(buildJsonSchema(request))
                            .build();

                    return eventSchemaRepository.save(schema)
                            .flatMap(saved -> unomiClient.createEventSchema(saved.getJsonSchema())
                                    .thenReturn(saved));
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<EventSchemaResponse> getSchemaById(Long id) {
        return eventSchemaRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("Schema not found")))
                .map(this::toResponse);
    }

    private EventSchemaResponse toResponse(EventSchema entity) {
        return EventSchemaResponse.builder()
                .id(entity.getId())
                .schemaVersion(entity.getSchemaVersion())
                .eventType(entity.getEventType())
                .sourceSystem(entity.getSourceSystem())
                .description(entity.getDescription())
                .jsonSchema(entity.getJsonSchema())
                .build();
    }

    private Map<String, Object> buildJsonSchema(EventSchemaRequest request) {
        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("$id", SCHEMA_BASE_URI + request.getEventType() + "/" + request.getSchemaVersion());
        schema.put("$schema", JSON_SCHEMA_DRAFT);
        schema.put("self", buildSelf(request));
        schema.put("title", request.getEventType() + "Event");
        schema.put("type", "object");
        schema.put("allOf", List.of(Map.of("$ref", UNOMI_EVENT_SCHEMA_REF)));
        schema.put("properties", buildProperties(request));
        schema.put("unevaluatedProperties", false);

        return schema;
    }

    private Map<String, Object> buildSelf(EventSchemaRequest request) {
        Map<String, Object> self = new LinkedHashMap<>();

        self.put("vendor", VENDOR);
        self.put("name", request.getEventType());
        self.put("format", "jsonschema");
        self.put("target", "events");
        self.put("version", request.getSchemaVersion());

        return self;
    }

    private Map<String, Object> buildProperties(EventSchemaRequest request) {
        Map<String, Object> root = new LinkedHashMap<>();

        root.put("source", Map.of("$ref", UNOMI_ITEM_SCHEMA_REF));
        root.put("target", Map.of("$ref", UNOMI_ITEM_SCHEMA_REF));
        root.put("properties", buildEventProperties(request));

        return root;
    }

    private Map<String, Object> buildEventProperties(EventSchemaRequest request) {
        Map<String, Object> eventProperties = new LinkedHashMap<>();
        Map<String, Object> fields = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        eventProperties.put("type", "object");

        for (EventFieldRequest field : request.getFields()) {
            fields.put(field.getName(), Map.of(
                    "type", List.of(field.getType().toLowerCase(), "null")
            ));

            if (field.isRequired()) {
                required.add(field.getName());
            }
        }

        eventProperties.put("properties", fields);
        if (!required.isEmpty()) {
            eventProperties.put("required", required);
        }

        return eventProperties;
    }
}
