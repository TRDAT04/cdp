package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Vai trò giao dịch của một profile (phục vụ badge "Vai trò giao dịch" + 2 số liệu
 * "X lần là Người gửi" / "Y lần là Người nhận" trên tab Tổng quan).
 *
 * <p>Field nào chưa có dữ liệu nguồn sẽ để {@code null} (không bỏ field), {@code roles}
 * để danh sách rỗng.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRolesResponse {

    /** Vai trò chính (VD: "Người gửi (chính)", "Chủ shop"). Null nếu chưa xác định. */
    private String primaryRole;

    /** Toàn bộ vai trò giao dịch. Danh sách rỗng nếu chưa có dữ liệu. */
    private List<String> roles;

    /** Số lần đóng vai trò Người gửi. Null nếu chưa có dữ liệu. */
    private Long senderCount;

    /** Số lần đóng vai trò Người nhận. Null nếu chưa có dữ liệu. */
    private Long receiverCount;
}
