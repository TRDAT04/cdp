package vn.vnpost.cdp.customer_event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EventFieldRequest {
    @NotBlank
    private String name;

    @NotNull
    private String type;

    private boolean required;

}
