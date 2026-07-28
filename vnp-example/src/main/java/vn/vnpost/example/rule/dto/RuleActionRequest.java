package vn.vnpost.example.rule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class RuleActionRequest {

    @NotNull(message = "action type must not be null")
    private ActionType type;

    private String eventProperty;

    @NotNull(message = "profileProperty must not be null")
    private String profileProperty;

    private String defaultValue;
}
