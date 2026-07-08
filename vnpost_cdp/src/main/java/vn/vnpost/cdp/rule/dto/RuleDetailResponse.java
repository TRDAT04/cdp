package vn.vnpost.cdp.rule.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDetailResponse {
    private String id;
    private String name;
    private String description;
    private String scope;
    private Boolean enabled;
    private Integer priority;
    private Map<String, Object> condition;
    private List<Map<String, Object>> actions;

}
