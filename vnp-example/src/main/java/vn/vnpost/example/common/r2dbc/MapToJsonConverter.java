package vn.vnpost.example.common.r2dbc;

import io.r2dbc.postgresql.codec.Json;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Ghi {@code Map<String, Object>} (field entity) xuống cột {@code jsonb} của Postgres.
 */
@WritingConverter
public class MapToJsonConverter implements Converter<Map<String, Object>, Json> {

    private final ObjectMapper objectMapper;

    public MapToJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Json convert(Map<String, Object> source) {
        return Json.of(objectMapper.writeValueAsString(source));
    }
}
