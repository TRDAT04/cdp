package vn.vnpost.cdp.unomi.dto;

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
public class UnomiProfileResponse {

    private String itemId;

    private String itemType;

    private Integer version;

    private Map<String, Object> properties;

    private Map<String, Object> systemProperties;

    private List<String> segments;

    private Map<String, Object> scores;

    private Map<String, Object> consents;
}
