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
public class ProfileUnomiSyncLogDetailResponse {
    private Long id;
    private String syncType;
    private Short status;
    private String statusText;
    private String errorMessage;
    private LocalDateTime syncedAt;
}
