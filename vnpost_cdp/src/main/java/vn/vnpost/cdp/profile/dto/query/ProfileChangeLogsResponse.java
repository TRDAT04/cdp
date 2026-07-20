package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Tab "Nhật ký": lịch sử thay đổi + lần đồng bộ Unomi gần nhất.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileChangeLogsResponse {

    private List<ProfileChangeLogResponse> changeLogs;

    private ProfileUnomiSyncLogDetailResponse latestUnomiSync;
}
