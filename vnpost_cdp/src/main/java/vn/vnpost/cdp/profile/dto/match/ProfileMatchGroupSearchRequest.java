package vn.vnpost.cdp.profile.dto.match;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Filter của màn "Đối soát định danh". Phân trang lấy từ {@code Pageable} của Spring Data,
 * không khai báo page/size ở đây.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProfileMatchGroupSearchRequest {

    /** Tìm theo tên/tên công ty, mã hồ sơ, số điện thoại hoặc MST. */
    private String keyword;
}
