package vn.vnpost.example.common.r2dbc;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Đăng ký converter Map&lt;String, Object&gt; &lt;-&gt; jsonb (Postgres) cho Spring Data R2DBC,
 * tương đương {@code @JdbcTypeCode(SqlTypes.JSON)} phía JPA.
 */
@Configuration
public class R2dbcJsonConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(ConnectionFactory connectionFactory,
                                                          ObjectMapper objectMapper) {
        R2dbcDialect dialect = DialectResolver.getDialect(connectionFactory);
        List<Object> converters = List.of(
                new MapToJsonConverter(objectMapper),
                new JsonToMapConverter(objectMapper)
        );
        return R2dbcCustomConversions.of(dialect, converters);
    }
}
