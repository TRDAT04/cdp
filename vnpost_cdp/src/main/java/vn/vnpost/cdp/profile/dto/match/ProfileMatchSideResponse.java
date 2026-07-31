package vn.vnpost.cdp.profile.dto.match;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMatchSideResponse {

    private Long masterProfileId;
    private String profileCode;
    private String sourceSystem;
    private String sourceCustomerId;
    private String fullName;
    private String phone;
    private String email;
    private String identityNo;
    private String taxCode;
    private LocalDate dateOfBirth;
    private String gender;
    private String customerType;
    private String customerTypeText;
    private String provinceCode;
    private String provinceName;
    private String unitCode;
    private String unitName;

    /**
     * Trạng thái của master profile (không phải của candidate). Cho phép UI vô hiệu hoá nút Gộp
     * khi một vế đã MERGED/DELETED, thay vì để người dùng bấm rồi nhận lỗi INVALID_PROFILE.
     */
    private Short profileStatus;
    private String profileStatusText;
}
