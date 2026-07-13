package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileListItemResponse {
    // ---- Dữ liệu từ PostgreSQL (MasterProfile) ----
    private Long id;
    private String fullName;
    private String avatarText;
    private String profileCode;
    private String phone;
    private String email;
    private String customerType;
    private String customerTypeText;
    private String warningStatus;
    private String warningText;
    private List<String> sourceSystems;
    private LocalDateTime lastActivityAt;
    private Short status;
    private String statusText;

    // ---- Dữ liệu hành vi từ Apache Unomi ----

    /** Danh sách segment ID mà profile đang thuộc. Mặc định [] khi Unomi không phản hồi. */
    @Builder.Default
    private List<String> segments = Collections.emptyList();

    /** Thời điểm truy cập đầu tiên (UTC). */
    private Instant firstVisit;

    /** Thời điểm truy cập trước lần cuối cùng (UTC). */
    private Instant previousVisit;

    /** Thời điểm truy cập gần nhất (UTC). */
    private Instant lastVisit;

    /** Tổng số lần truy cập. */
    private Integer nbOfVisits;

    /** Tổng số lần mua hàng. */
    private Integer purchaseCount;

    /** Tổng giá trị giao dịch. */
    private BigDecimal totalSpent;

    /** Thời điểm giao dịch gần nhất (UTC). */
    private Instant lastTransactionDate;
}

