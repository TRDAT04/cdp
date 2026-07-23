package vn.vnpost.cdp.profile.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Kiểm tra quan hệ hợp lệ giữa {@link CustomerType} và {@link CustomerGroup}.
 *
 * <p>Hai enum được thiết kế tách rời; ràng buộc "group phụ thuộc type" được
 * tập trung ở đây thay vì nhúng vào từng enum, để quy tắc dễ đọc và dễ mở rộng.</p>
 *
 * <ul>
 *     <li>{@link CustomerType#PERSONAL} → {@link CustomerGroup#PERSONAL_NORMAL}</li>
 *     <li>{@link CustomerType#BUSINESS} → {@link CustomerGroup#SME}, {@link CustomerGroup#KHL}</li>
 * </ul>
 */
public final class CustomerClassificationValidator {

    /** Bảng nhóm hợp lệ theo từng loại khách hàng. */
    private static final Map<CustomerType, Set<CustomerGroup>> ALLOWED_GROUPS = Map.of(
            CustomerType.PERSONAL, EnumSet.of(CustomerGroup.PERSONAL_NORMAL),
            CustomerType.BUSINESS, EnumSet.of(CustomerGroup.SME, CustomerGroup.KHL)
    );

    private CustomerClassificationValidator() {
    }

    /** Danh sách nhóm hợp lệ cho một loại khách hàng ({@code null} → rỗng). */
    public static Set<CustomerGroup> allowedGroups(CustomerType type) {
        if (type == null) return Set.of();
        return ALLOWED_GROUPS.getOrDefault(type, Set.of());
    }

    /**
     * Cặp (type, group) có hợp lệ không.
     *
     * <p>{@code group == null} được coi là hợp lệ (nhóm không bắt buộc). Nếu
     * muốn bắt buộc phải có nhóm, kiểm tra riêng ở tầng gọi.</p>
     */
    public static boolean isValid(CustomerType type, CustomerGroup group) {
        if (type == null) return false;
        if (group == null) return true;
        return allowedGroups(type).contains(group);
    }

    /**
     * Phiên bản làm việc trực tiếp trên chuỗi lưu trong DB (tolerant).
     * Chuỗi không nhận diện được sẽ bị coi là không hợp lệ.
     */
    public static boolean isValidRaw(String rawType, String rawGroup) {
        CustomerType type = CustomerType.fromValue(rawType);
        if (type == null) return false;
        if (rawGroup == null || rawGroup.isBlank()) return true;
        CustomerGroup group = CustomerGroup.fromValue(rawGroup);
        if (group == null) return false;
        return isValid(type, group);
    }
}
