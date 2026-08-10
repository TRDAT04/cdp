package vn.vnpost.cdp.profile.dto.match;

import lombok.*;

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
    private String customerType;
    private String provinceCode;
    private String provinceName;
    private String unitCode;
    private String unitName;
}
