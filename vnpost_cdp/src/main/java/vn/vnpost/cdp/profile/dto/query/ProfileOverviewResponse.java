package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Tab "Tổng quan".
 * <p>NOTE: chưa có trường "Vai trò giao dịch (Người gửi/Chủ shop + số lần)" vì
 * hiện không có dữ liệu nguồn tương ứng trong entity/Unomi.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileOverviewResponse {

    private Long id;
    private String profileCode;

    private String fullName;
    private String gender;
    private LocalDate dateOfBirth;
    private String phone;
    private String email;

    /** CCCD đã che 1 phần. */
    private String identityNoMasked;

    /** Post ID lấy từ identity link có sourceSystem = POSTID (nếu có). */
    private String postId;

    private String customerType;
    private String customerTypeText;

    // Bưu cục quản lý
    private String provinceCode;
    private String provinceName;
    private String unitCode;
    private String unitName;

    /** Phân khúc hiện tại (segment) lấy từ Unomi. */
    private List<String> segments;
}
