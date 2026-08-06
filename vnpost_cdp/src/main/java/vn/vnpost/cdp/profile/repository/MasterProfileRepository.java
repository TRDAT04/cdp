package vn.vnpost.cdp.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.vnpost.cdp.profile.entity.MasterProfile;

import java.util.List;
import java.util.Optional;

@Repository
public interface MasterProfileRepository extends JpaRepository<MasterProfile, Long>,
        JpaSpecificationExecutor<MasterProfile> {

    Optional<MasterProfile> findByProfileCode(String profileCode);

    boolean existsByProfileCode(String profileCode);

    // ── Các khóa định danh dưới đây PHẢI trả List, không được trả Optional ──
    // Trong schema (schema.sql) chúng chỉ có INDEX thường, KHÔNG có UNIQUE constraint — và trùng
    // lặp chính là thứ hệ thống này tồn tại để phát hiện. Nếu trả Optional, Spring Data ném
    // IncorrectResultSizeDataAccessException ngay khi có 2 hồ sơ trùng SĐT/email/CCCD/MST, tức là
    // matcher chết đúng vào case cần xử lý (VD SĐT dùng chung giữa người thân / shipper).

    List<MasterProfile> findByPhone(String phone);

    List<MasterProfile> findByEmail(String email);

    List<MasterProfile> findByIdentityNo(String identityNo);

    List<MasterProfile> findByTaxCode(String taxCode);
}
