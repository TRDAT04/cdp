# CDP VNPost — Customer Profile: Định danh & Matching (changelog tóm tắt)

**Ngày:** 2026-07-21 · **Hệ thống:** Customer Profile / CDP VNPost · **Trạng thái:** code xong, **chưa build/deploy**

---

## 1. Vấn đề ban đầu
- Rà soát source theo UI mockup → phát hiện thiếu field định danh: `taxCode/MST`, `khlCode`, `crmId`, `appUserId`, `postId` không có cột/định danh chuẩn; `postId` chỉ có ở tab Tổng quan.
- `customerType` là free-text, chỉ hỗ trợ PERSONAL/VIP/FREQUENT — thiếu BUSINESS/KHL/SHOP_OWNER/TMDT; VIP/FREQUENT là "hạng khách" bị nhét nhầm vào `customerType`.
- Matching: chỉ match theo phone/email/CCCD/sourceCustomerId. MST, KHL, và các identity_type mới **không được dùng để tìm/khớp** dù đã ghi vào DB.

## 2. Đã thay đổi
**Database** (chạy migration THỦ CÔNG — `ddl-auto=none`)
- `master_profiles`: + `tax_code`, `customer_tier`, `customer_group` (vá gap). Đều nullable → **không ảnh hưởng dữ liệu cũ**.
- Data migration: `FREQUENT/VIP` chuyển từ `customer_type` → `customer_tier`; reset `customer_type` = BUSINESS (có MST) / PERSONAL.
- Chuẩn hóa link cũ: nguồn POSTID `SOURCE_CUSTOMER_ID` → `POST_ID`.

**API** — chỉ THÊM, **không breaking**
- `ProfileDetailResponse` + `ProfileOverviewResponse`: thêm `taxCode`, `customerTier`, object `identities` (postId, crmId, khlCode, appUserId, deviceId, cookieId, paymentId; field trống = `null`).
- `ProfileListItemResponse` (API list `GET /api/v1/admin/profiles`): thêm `taxCode`, `customerTier`, `khlCode` (scalar). **Cố ý KHÔNG** nhét object `identities` đầy đủ vào list (tránh response nặng) — các định danh khác chỉ có ở Detail.
- Giữ `postId` top-level ở Overview (đánh dấu `@Deprecated`) để tương thích ngược.

**Logic matching/merge**
- Chuẩn hóa theo `eventType`: `PROFILE_CREATED` → field cứng vào master; `PROFILE_ENRICHED` → định danh vào `profile_identity_links` (upsert, không trùng).
- Fast-path đã thêm: **FP3** enrichment (phone/email khớp → AUTO_MERGE); **FP2b** MST deterministic (khác→REVIEW, khớp + tên ≥75% → AUTO_MERGE); **FP2c** khớp khóa duy nhất KHL/CRM/PostID/AppUserId/Payment → AUTO_MERGE.
- Discovery mở rộng: tìm thêm theo `taxCode` + identity_type mạnh.
- **Giữ nguyên**: bảng điểm scoring & ngưỡng 70/95; rule phone/email/CCCD; FP1 (alreadyLinked), FP2 (CCCD).

**Enum/chuẩn hóa**
- Mới: `CustomerType` (5 giá trị chuẩn), `IdentityType` (danh mục identity_type).
- `ProfileSourceSystemCode` + PORTAL_KHL/WEBSITE/PAYPOST; `ProfileIngestionEventType` + PROFILE_ENRICHED.

## 3. CHƯA làm / để lại cho sau
- **#9 SĐT+Email (non-enrichment):** giữ CREATE_MATCH_CANDIDATE (review), **chưa** auto-merge. Lý do: chưa có field "vai trò khách hàng" để check xung đột → mở lại sau khi có field vai trò.
- **DEVICE_ID / COOKIE_ID:** cố ý loại khỏi auto-merge → để dành **Probabilistic Matching** (chưa trong scope).
- **FP2b/FP2c cho luồng admin dedup** (`ProfileMatchCandidateServiceImpl`): **chưa** áp dụng, mới chỉ ở ingestion. (Chờ xác nhận.)
- **Test:** chưa viết (project chưa có `src/test`; E2E 4-event cần Testcontainers).
- **Build:** chưa chạy `mvn compile` (máy không có maven) — cần verify trước commit.

## 4. Rủi ro / tác động vận hành
- **CSKH sẽ thấy:** nhiều cảnh báo `warningStatus=CONFLICT`/`NEED_REVIEW` hơn — do Discovery rộng hơn có thể ra nhiều candidate → CONFLICT (đây là hành vi AN TOÀN, không tự merge sai).
- **customerType đổi giá trị:** sau migration, bản ghi VIP/FREQUENT thành PERSONAL/BUSINESS; hạng khách nằm ở `customerTier`.
- **Cần báo trước khi deploy:** (1) DBA — chạy migration đúng thứ tự; (2) team FE — response có field mới + danh mục customerType đổi; (3) nguồn dữ liệu (MyVNPost/Website/PayPost) — set `eventType=PROFILE_ENRICHED` + gửi key định danh mới.

## 5. Việc cần làm tiếp (ưu tiên giảm dần)
1. Chạy `mvn compile` + fix nếu lỗi, rồi chạy migration trên UAT.
2. Chốt việc áp FP2b/FP2c cho admin dedup hay không.
3. FE cập nhật theo response/danh mục mới; nguồn cập nhật eventType + payload.
4. Bổ sung test (unit decision + normalization) khi có hạ tầng.
5. Sau khi có field "vai trò KH" → mở lại auto-merge cho #9.

## 6. File đã thay đổi
- `src/main/resources/db/profile_identity_fields_migration.sql` (mới)
- `src/main/resources/db/schema.sql`
- `profile/enums/CustomerType.java` (mới)
- `profile/enums/IdentityType.java` (mới)
- `ingestion/enums/ProfileIngestionEventType.java`
- `ingestion/enums/ProfileSourceSystemCode.java`
- `profile/entity/MasterProfile.java`
- `ingestion/dto/NormalizedProfileData.java`
- `profile/dto/query/ProfileIdentitiesResponse.java` (mới)
- `profile/dto/query/ProfileDetailResponse.java`
- `profile/dto/query/ProfileOverviewResponse.java`
- `profile/dto/query/ProfileListItemResponse.java`
- `ingestion/service/ProfileNormalizationService.java`
- `ingestion/service/ProfileMergeExecutorService.java`
- `ingestion/service/ProfileMergeDecisionService.java`
- `ingestion/service/ProfileMatchingService.java`
- `profile/repository/ProfileIdentityLinkRepository.java`
- `profile/repository/MasterProfileRepository.java`
- `profile/assembler/ProfileDetailAssembler.java`
- `profile/assembler/ProfileListAssembler.java`
- `profile/service/ProfileQueryServiceImpl.java`
