package vn.vnpost.cdp.rule.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import vn.vnpost.cdp.rule.dto.ActionType;


@Data
public class RuleActionConfig {
    @NotNull(message = "action type must not be null")
    private ActionType type;

    private String eventProperty;

    @NotNull(message = "profileProperty must not be null")
    private String profileProperty;

    private String defaultValue;
}
