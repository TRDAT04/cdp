package vn.vnpost.cdp.unomi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body gửi lên POST /cxs/profiles/search của Apache Unomi.
 *
 * <pre>{@code
 * {
 *   "condition": { ... },
 *   "offset": 0,
 *   "limit": 10
 * }
 * }</pre>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnomiProfileSearchRequest {

    @JsonProperty("condition")
    private UnomiCondition condition;

    @JsonProperty("offset")
    @Builder.Default
    private int offset = 0;

    @JsonProperty("limit")
    @Builder.Default
    private int limit = 100;
}
