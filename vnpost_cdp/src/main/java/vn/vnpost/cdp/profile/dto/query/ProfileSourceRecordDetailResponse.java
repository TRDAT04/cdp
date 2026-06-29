package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileSourceRecordDetailResponse {
    private Long id;
    private String sourceSystem;
    private String sourceCustomerId;
    private String sourceEventId;
    private Short mergeStatus;
    private String mergeStatusText;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private String errorMessage;
    private Map<String, Object> rawPayload;
    private Map<String, Object> normalizedPayload;
}
