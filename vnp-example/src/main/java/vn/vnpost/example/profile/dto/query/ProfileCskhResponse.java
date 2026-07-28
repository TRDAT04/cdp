package vn.vnpost.example.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Tab "CSKH": tổng hợp khiếu nại của một profile, suy diễn runtime từ {@code customer_events}
 * (join event {@code complaintCreated} + {@code complaintResolved} theo {@code complaintId}).
 *
 * <p>Nếu profile CHƯA có event {@code complaintCreated} nào → cả 4 field {@code null}
 * (phân biệt "chưa từng có khiếu nại" với "có khiếu nại nhưng tỷ lệ 0%").</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileCskhResponse {

    /** Số complaintId DISTINCT từ event complaintCreated. Null nếu chưa có khiếu nại. */
    private Long totalComplaints;

    /** Số khiếu nại status=OPEN mà chưa có complaintResolved tương ứng. */
    private Long openComplaints;

    /** Điểm hài lòng trung bình (AVG satisfactionScore của các complaintResolved có giá trị). */
    private BigDecimal satisfactionScore;

    /** Tỷ lệ giải quyết đúng SLA (0-1): resolvedAt <= slaDeadline / tổng đã resolved. */
    private BigDecimal slaOnTimeRate;
}
