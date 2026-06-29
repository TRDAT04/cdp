package vn.vnpost.cdp.profile.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUnomiSyncLogResponse {

    private Long id;
    private Long masterProfileId;
    private String profileCode;
    private String syncType;
    private Map<String, Object> requestPayload;
    private Map<String, Object> responsePayload;
    private Short status;
    private String errorMessage;
    private LocalDateTime syncedAt;
    private String createdBy;
}
