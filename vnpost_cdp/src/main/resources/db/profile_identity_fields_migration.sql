-- ============================================================
-- CDP Migration: Identity fields + CustomerType/Tier normalization
-- Apply AFTER schema.sql and profile_identity_schema.sql.
-- Idempotent: an toàn khi chạy lại nhiều lần.
-- (ddl-auto=none + sql.init.mode=never => phải chạy file này THỦ CÔNG)
-- ============================================================

-- ------------------------------------------------------------
-- 1. master_profiles: thêm cột định danh / phân loại mới
-- ------------------------------------------------------------

-- 1.1 tax_code (MST) — TÁCH RIÊNG khỏi identity_no (CCCD giữ nguyên, KHÔNG đụng)
ALTER TABLE master_profiles ADD COLUMN IF NOT EXISTS tax_code VARCHAR(50);
CREATE INDEX IF NOT EXISTS idx_master_profiles_tax_code ON master_profiles(tax_code);
COMMENT ON COLUMN master_profiles.tax_code IS
    'Mã số thuế (MST) cho khách hàng doanh nghiệp/KHL. KHÁC identity_no (CCCD cá nhân).';

-- 1.2 customer_tier — tách "hạng khách" (VIP/FREQUENT) ra khỏi customer_type
ALTER TABLE master_profiles ADD COLUMN IF NOT EXISTS customer_tier VARCHAR(50);
COMMENT ON COLUMN master_profiles.customer_tier IS
    'Hạng khách hàng (VIP, FREQUENT...). customer_type chỉ chứa PERSONAL/BUSINESS/KHL/SHOP_OWNER/TMDT.';

-- 1.3 customer_group — đồng bộ với entity MasterProfile (cột đã tồn tại trong entity
--     nhưng thiếu trong schema.sql). IF NOT EXISTS nên an toàn nếu prod đã có.
ALTER TABLE master_profiles ADD COLUMN IF NOT EXISTS customer_group VARCHAR(50);

COMMENT ON COLUMN master_profiles.customer_type IS
    'Loại KH (danh mục chuẩn: PERSONAL, BUSINESS, KHL, SHOP_OWNER, TMDT).';

-- ------------------------------------------------------------
-- 2. profile_identity_links: cập nhật danh mục identity_type
--    (cột là VARCHAR tự do; enum IdentityType phía app là "danh mục" chuẩn)
-- ------------------------------------------------------------
COMMENT ON COLUMN profile_identity_links.identity_type IS
    'Loại định danh. Danh mục: IDENTITY_NO, PHONE, EMAIL, SOURCE_CUSTOMER_ID, '
    'POST_ID, CRM_ID, KHL_CODE, APP_USER_ID, DEVICE_ID, COOKIE_ID, PAYMENT_ID.';

-- 2.1 Chuẩn hóa PostID cũ: link nguồn POSTID đang lưu identity_type=SOURCE_CUSTOMER_ID
--     => POST_ID (tránh tạo trùng loại khi ingestion mới ghi POST_ID).
UPDATE profile_identity_links
   SET identity_type = 'POST_ID'
 WHERE UPPER(source_system) = 'POSTID'
   AND identity_type = 'SOURCE_CUSTOMER_ID';

-- ------------------------------------------------------------
-- 3. DATA MIGRATION: FREQUENT / VIP  ->  customer_tier, reset customer_type
-- ------------------------------------------------------------

-- 3.1 Copy giá trị hạng cũ sang cột mới customer_tier
UPDATE master_profiles
   SET customer_tier = UPPER(customer_type)
 WHERE UPPER(customer_type) IN ('FREQUENT', 'VIP')
   AND customer_tier IS NULL;

-- 3.2 Suy lại customer_type hợp lệ cho các bản ghi vừa tách:
--       có tax_code            -> BUSINESS
--       ngược lại (kể cả có CCCD hoặc trống) -> PERSONAL (mặc định an toàn)
UPDATE master_profiles
   SET customer_type = CASE
        WHEN tax_code IS NOT NULL AND tax_code <> '' THEN 'BUSINESS'
        ELSE 'PERSONAL'
   END
 WHERE UPPER(customer_type) IN ('FREQUENT', 'VIP');

-- 3.3 Chuẩn hóa alias tiếng Việt cũ (nếu tồn tại trong dữ liệu)
UPDATE master_profiles SET customer_type = 'PERSONAL' WHERE UPPER(customer_type) = 'CA_NHAN';
UPDATE master_profiles SET customer_type = 'BUSINESS' WHERE UPPER(customer_type) = 'DOANH_NGHIEP';
UPDATE master_profiles SET customer_type = 'SHOP_OWNER' WHERE UPPER(customer_type) = 'CHU_SHOP';
