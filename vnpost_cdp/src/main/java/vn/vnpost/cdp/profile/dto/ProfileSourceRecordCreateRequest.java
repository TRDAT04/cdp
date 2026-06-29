package vn.vnpost.cdp.profile.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
public class ProfileSourceRecordCreateRequest {
    private String sourceSystem;
    private String sourceCustomerId;
    private String sourceEventId;
    private Long masterProfileId;
    private String identityKey;
    private Map<String, Object> rawPayload;
    private Map<String, Object> normalizedPayload;
    private LocalDateTime receivedAt;
}
