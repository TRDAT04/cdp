package vn.vnpost.cdp.unomi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnomiEventItem {
    private String eventType;
    private String scope;
    private String profileId;
    private Map<String, Object> source;
    private Map<String, Object> target;
    private Map<String, Object> properties;
}
