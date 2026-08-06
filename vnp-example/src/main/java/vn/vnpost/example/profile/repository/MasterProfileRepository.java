package vn.vnpost.example.profile.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import vn.vnpost.example.profile.entity.MasterProfile;

@Repository
public interface MasterProfileRepository extends ReactiveCrudRepository<MasterProfile, Long> {

    // ── Các khóa định danh dưới đây PHẢI trả Flux, không được trả Mono ──
    // Trong schema chúng chỉ có INDEX thường, KHÔNG có UNIQUE constraint — và trùng lặp chính là
    // thứ hệ thống này tồn tại để phát hiện. Nếu trả Mono, Spring Data R2DBC ném
    // IncorrectResultSizeDataAccessException ngay khi có 2 hồ sơ trùng SĐT/email/CCCD/MST, tức là
    // matcher chết đúng vào case cần xử lý (VD SĐT dùng chung giữa người thân / shipper).

    Flux<MasterProfile> findByIdentityNo(String identityNo);

    Flux<MasterProfile> findByPhone(String phone);

    Flux<MasterProfile> findByEmail(String email);

    Flux<MasterProfile> findByTaxCode(String taxCode);
}
