package vn.vnpost.cdp.unomi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnomiProfileResponse {

    private String itemId;

    private String itemType;

    private Integer version;

    /** Typed properties thay vì Map&lt;String, Object&gt; để type-safe và dễ bảo trì. */
    private UnomiProfileProperties properties;

    private Map<String, Object> systemProperties;

    /** Danh sách segment ID mà profile đang thuộc. */
    private List<String> segments;

    private Map<String, Object> scores;

    private Map<String, Object> consents;
}
