package vn.vnpost.example.ingestion.dto;

import vn.vnpost.example.ingestion.enums.MergeDecision;
import vn.vnpost.example.profile.entity.MasterProfile;

/**
 * Kết quả của ProfileMergeDecisionService.decide().
 *
 * <p>Phải mang theo {@code target}: khi có nhiều candidate, decide() tách bằng khóa mạnh và hồ sơ
 * được chọn KHÔNG chắc nằm ở index 0 của danh sách candidate. Nếu chỉ trả về enum, caller buộc phải
 * đoán bằng {@code candidates.get(0)} và sẽ merge dữ liệu vào sai hồ sơ khách hàng.
 *
 * @param decision hành động cần thực hiện
 * @param target   hồ sơ đích đã chọn — null với CREATE_NEW_PROFILE / REJECT / CONFLICT
 */
public record MergeDecisionResult(MergeDecision decision, MasterProfile target) {

    public static MergeDecisionResult of(MergeDecision decision) {
        return new MergeDecisionResult(decision, null);
    }

    public static MergeDecisionResult of(MergeDecision decision, MasterProfile target) {
        return new MergeDecisionResult(decision, target);
    }
}
