package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tab "Nhật ký": lịch sử thay đổi + lần đồng bộ Unomi gần nhất + tóm tắt vòng đời hồ sơ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileChangeLogsResponse {

//    private List<ProfileChangeLogResponse> changeLogs;
//
//    private ProfileUnomiSyncLogDetailResponse latestUnomiSync;

    /** Tóm tắt vòng đời hồ sơ, suy diễn từ toàn bộ change logs + lần sync gần nhất. */
    private ProfileSummary profileSummary;


    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileSummary {

        /** DISTINCT sourceSystem của hồ sơ — tái sử dụng logic Overview/Detail (KHÔNG suy từ changeLogs). */
        private List<String> sourceSystems;

        /** Thời điểm tạo hồ sơ = MIN(changedAt) của dòng mergeStrategy=CREATE_NEW_PROFILE. Null nếu không có. */
        private LocalDateTime createdAt;

        /** Cập nhật gần nhất = MAX giữa MAX(changeLogs.changedAt) và latestUnomiSync.syncedAt. */
        private LocalDateTime lastUpdatedAt;

        /**
         * Phiên bản hồ sơ. Hiện {@code null} — master_profiles chưa có cột version/@Version
         * (optimistic locking). Đang xác nhận nguồn field này riêng.
         */
        private Integer profileVersion;

        /** Hệ nguồn tạo hồ sơ = newSource của dòng có changedAt sớm nhất. Null nếu không có. */
        private String createdBySystem;
    }
}
