package vn.vnpost.cdp.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ProfileUnomiSyncLogCreateRequest {

    @NotNull
    private Long masterProfileId;

    @NotBlank
    private String profileCode;

    private String syncType;

    private Map<String, Object> requestPayload;

    private Map<String, Object> responsePayload;
}
