package vn.vnpost.cdp.profile.dto.match;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Một dòng của màn "Đối soát định danh": gom toàn bộ candidate PENDING đang tham chiếu tới
 * MỘT hồ sơ gốc thành một dòng tổng hợp.
 *
 * <p>Khác {@link ProfileMatchCandidateResponse} — DTO đó mô tả một CẶP hồ sơ nghi trùng
 * (dùng cho màn đối chiếu chi tiết), còn DTO này mô tả một HỒ SƠ và các con số tổng hợp của nó.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMatchGroupResponse {

    private Long masterProfileId;
    private String profileCode;

    /** Tên cá nhân hoặc tên doanh nghiệp — {@code master_profiles} chỉ có một cột tên chung. */
    private String fullName;
    private String customerType;
    private String customerTypeText;
    private String phone;
    private String taxCode;
    private String identityNo;

    /** Số mã chờ xác nhận = số candidate PENDING có hồ sơ này ở vế left hoặc right. */
    private Long pendingCount;

    /** Tin cậy cao nhất trong các candidate PENDING của hồ sơ. */
    private BigDecimal maxScore;
    private String maxScorePercent;
    private String maxMatchLevel;
    private String maxMatchLevelText;

    /** Khoá khớp nổi bật: reasonType distinct, đã lọc bỏ các reason dạng {@code *_CONFLICT}. */
    private List<String> matchedKeys;
    private List<String> matchedKeysText;

    /** true nếu có ít nhất một candidate PENDING dưới ngưỡng tin cậy thấp. */
    private Boolean hasLowConfidence;
}
