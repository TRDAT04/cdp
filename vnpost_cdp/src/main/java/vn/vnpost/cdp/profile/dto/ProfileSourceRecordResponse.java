package vn.vnpost.cdp.profile.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileSourceRecordResponse {
    private Long id;
    private String sourceSystem;
    private String sourceCustomerId;
    private String sourceEventId;
    private Long masterProfileId;
    private String identityKey;
    private Map<String, Object> rawPayload;
    private Map<String, Object> normalizedPayload;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private Short mergeStatus;
    private String errorMessage;
    private String createdBy;
    private LocalDateTime created;
    private LocalDateTime modified;
    private String modifiedBy;
}
