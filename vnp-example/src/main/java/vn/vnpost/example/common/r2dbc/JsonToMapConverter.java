package vn.vnpost.example.common.r2dbc;

import io.r2dbc.postgresql.codec.Json;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Đọc cột {@code jsonb} của Postgres (kiểu {@link Json} của driver r2dbc-postgresql)
 * thành {@code Map<String, Object>} cho field entity.
 */
@ReadingConverter
public class JsonToMapConverter implements Converter<Json, Map<String, Object>> {

    private final ObjectMapper objectMapper;

    public JsonToMapConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> convert(Json source) {
        return objectMapper.readValue(source.asString(), new TypeReference<Map<String, Object>>() {
        });
    }
}
