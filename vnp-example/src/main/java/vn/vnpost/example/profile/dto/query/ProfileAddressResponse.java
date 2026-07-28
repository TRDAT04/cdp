package vn.vnpost.example.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tab "Địa chỉ".
 * <p>NOTE: hiện chỉ trả về province/unit đang có trên MasterProfile.
 * Địa chỉ chi tiết (số nhà, đường, phường/xã...) chưa được tách trường riêng
 * và cần spec/ảnh UI để xác nhận field.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileAddressResponse {

    private String provinceCode;
    private String provinceName;
    private String unitCode;
    private String unitName;
}
