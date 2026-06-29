package vn.vnpost.cdp.profile.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
public class ProfileSourceRecordUpdateRequest {
    private Long masterProfileId;
    private Map<String, Object> normalizedPayload;
    private Short mergeStatus;
    private String errorMessage;
    private LocalDateTime processedAt;
}
