package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileConflictResponse {
    private Long id;
    private String propertyName;
    private String currentValue;
    private String incomingValue;
    private String currentSource;
    private String incomingSource;
    private String conflictReason;
    private Short resolutionStatus;
    private String resolutionStatusText;
    private LocalDateTime created;
}
