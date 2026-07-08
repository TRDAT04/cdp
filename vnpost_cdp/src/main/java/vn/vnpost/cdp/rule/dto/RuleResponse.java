package vn.vnpost.cdp.rule.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResponse {
    private String id;
    private String name;
    private String description;
    private String scope;
    private Boolean enabled;
    private Boolean readOnly;
    private List<String> tags;

}