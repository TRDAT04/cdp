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
public class ProfileChangeLogResponse {
    private Long id;
    private String eventType;
    private String propertyName;
    private String oldValue;
    private String newValue;
    private String selectedValue;
    private String oldSource;
    private String newSource;
    private String mergeStrategy;
    private String reason;
    private String changedBy;
    private LocalDateTime changedAt;
}
