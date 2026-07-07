package vn.vnpost.cdp.rule.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;


@Data
public class RuleConfig {

    private String ruleId;

    @NotBlank(message = "rule name must not be blank")
    private String name;

    private String description;

    private String scope;

    @NotBlank(message = "eventType must not be blank")
    private String eventType;

    private int priority = 5;

    private boolean raiseEventOnlyOnceForProfile = false;
    private boolean raiseEventOnlyOnceForSession = false;

    @NotEmpty(message = "actions must not be empty — a rule with no actions is a no-op")
    @Valid
    private List<RuleActionConfig> actions;
}
