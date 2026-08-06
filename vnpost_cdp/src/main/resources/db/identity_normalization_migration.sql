-- =====================================================================
-- Chuẩn hoá dữ liệu định danh đã tồn tại về đúng dạng mà IdentityUtils sinh ra.
--
-- Lý do: trước đây tồn tại 3 đường ghi dữ liệu với 3 cách chuẩn hoá khác nhau
--   1. ProfileNormalizationService (luồng Kafka) — chỉ bỏ khoảng trắng/gạch, giữ "+84..."
--   2. MasterProfileServiceImpl   (API admin)   — ghi thô, không chuẩn hoá gì
--   3. IdentityUtils              (pool + scorer) — đổi "84..." thành "0..."
-- Giá trị được LƯU (1,2) khác giá trị dùng để TRUY VẤN (3), nên hai hồ sơ của cùng một
-- người ghi ở hai dạng khác nhau không bao giờ được ghép cặp để so.
--
-- Code nay đã dùng IdentityUtils ở cả 3 đường. File này xử lý phần dữ liệu cũ.
-- Chạy SAU khi deploy code mới.
--
-- Dạng chuẩn: SĐT = dạng nội địa có 0 đầu (0912345678); CCCD/MST = bỏ khoảng trắng, in hoa.
-- =====================================================================

-- Kiểm tra trước khi chạy: xem có bao nhiêu hàng sẽ bị đổi.
-- SELECT count(*) FROM master_profiles
--  WHERE phone IS NOT NULL AND phone <> regexp_replace(phone, '[^0-9]', '', 'g');

BEGIN;

-- ── 1. master_profiles.phone ──
-- Bước 1a: bỏ hết ký tự không phải chữ số ("+84 912-345-678" → "84912345678")
UPDATE master_profiles
   SET phone = regexp_replace(phone, '[^0-9]', '', 'g')
 WHERE phone IS NOT NULL
   AND phone <> regexp_replace(phone, '[^0-9]', '', 'g');

-- Bước 1b: bỏ tiền tố gọi quốc tế viết bằng 00 ("0084912345678" → "84912345678")
UPDATE master_profiles
   SET phone = substring(phone from 3)
 WHERE phone LIKE '00%';

-- Bước 1c: mã quốc gia 84 → 0 ("84912345678" → "0912345678").
-- Điều kiện length > 9 khớp đúng logic IdentityUtils.normalizePhone, để không phá số nội địa
-- hợp lệ vô tình bắt đầu bằng 84.
UPDATE master_profiles
   SET phone = '0' || substring(phone from 3)
 WHERE phone LIKE '84%'
   AND length(phone) > 9;

-- Bước 1d: chuỗi rỗng sau khi lọc (VD phone cũ là "N/A") phải về NULL — để rỗng thì
-- findByPhone('') sẽ khớp hàng loạt hồ sơ không liên quan.
UPDATE master_profiles SET phone = NULL WHERE phone = '';

-- ── 2. master_profiles.email ──
UPDATE master_profiles
   SET email = lower(btrim(email))
 WHERE email IS NOT NULL
   AND email <> lower(btrim(email));

UPDATE master_profiles SET email = NULL WHERE email = '';

-- ── 3. master_profiles.identity_no và tax_code ──
-- Bỏ mọi khoảng trắng, in hoa. KHÔNG bỏ dấu gạch ngang: với MST thì "0101234567-001" là
-- chi nhánh còn "0101234567" là trụ sở — hai pháp nhân khác nhau.
UPDATE master_profiles
   SET identity_no = upper(regexp_replace(identity_no, '\s+', '', 'g'))
 WHERE identity_no IS NOT NULL
   AND identity_no <> upper(regexp_replace(identity_no, '\s+', '', 'g'));

UPDATE master_profiles SET identity_no = NULL WHERE identity_no = '';

UPDATE master_profiles
   SET tax_code = upper(regexp_replace(tax_code, '\s+', '', 'g'))
 WHERE tax_code IS NOT NULL
   AND tax_code <> upper(regexp_replace(tax_code, '\s+', '', 'g'));

UPDATE master_profiles SET tax_code = NULL WHERE tax_code = '';

-- ── 4. profile_identity_links.identity_value ──
-- Pool candidate tra bằng cột này (findByIdentityTypeAndIdentityValue) nên nó phải cùng dạng
-- chuẩn với giá trị mà code sinh ra, nếu không link PHONE/EMAIL cũ sẽ không khớp được.
UPDATE profile_identity_links
   SET identity_value = regexp_replace(identity_value, '[^0-9]', '', 'g')
 WHERE identity_type = 'PHONE'
   AND identity_value IS NOT NULL
   AND identity_value <> regexp_replace(identity_value, '[^0-9]', '', 'g');

UPDATE profile_identity_links
   SET identity_value = substring(identity_value from 3)
 WHERE identity_type = 'PHONE' AND identity_value LIKE '00%';

UPDATE profile_identity_links
   SET identity_value = '0' || substring(identity_value from 3)
 WHERE identity_type = 'PHONE'
   AND identity_value LIKE '84%'
   AND length(identity_value) > 9;

UPDATE profile_identity_links
   SET identity_value = lower(btrim(identity_value))
 WHERE identity_type = 'EMAIL'
   AND identity_value IS NOT NULL
   AND identity_value <> lower(btrim(identity_value));

UPDATE profile_identity_links
   SET identity_value = upper(regexp_replace(identity_value, '\s+', '', 'g'))
 WHERE identity_type IN ('IDENTITY_NO', 'TAX_CODE')
   AND identity_value IS NOT NULL
   AND identity_value <> upper(regexp_replace(identity_value, '\s+', '', 'g'));

COMMIT;

-- =====================================================================
-- Index phụ trợ cho pool candidate.
--
-- ingestion_indexes.sql đã có idx_pil_type_value trên (identity_type, identity_value) NHƯNG là
-- PARTIAL INDEX với "WHERE status = 1". Postgres chỉ dùng được partial index khi predicate của
-- query bao hàm predicate của index — mà findByIdentityTypeAndIdentityValue() sinh ra
-- "WHERE identity_type = ? AND identity_value = ?" KHÔNG có điều kiện status (code lọc status=1
-- ở tầng Java sau khi lấy về). Nên index đó không dùng được cho đúng truy vấn nóng nhất.
--
-- Vì vậy phải tạo index KHÔNG partial, và phải đặt TÊN KHÁC: dùng lại tên cũ với
-- "CREATE INDEX IF NOT EXISTS" sẽ là no-op âm thầm (tên đã tồn tại) → tưởng đã thêm mà chưa.
--
-- Việc bổ sung khớp khóa typed ở luồng admin làm truy vấn này chạy nhiều hơn trước.
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pil_type_value_all
    ON profile_identity_links(identity_type, identity_value);

-- Ghi chú cùng vấn đề: idx_pil_src_customer cũng là partial (WHERE status = 1).
-- findBySourceSystemAndSourceCustomerIdAndStatus(..., 1) dùng được nó; còn
-- findBySourceSystemAndSourceCustomerId(...) (không truyền status, dùng ở ProfileMatchingService)
-- thì không. Chưa tạo index đầy đủ cho nhánh này vì lượng gọi thấp hơn — theo dõi thêm nếu chậm.

-- =====================================================================
-- SAU KHI CHẠY: dữ liệu cũ trùng lặp mà trước đây không khớp được (vì lệch dạng) giờ đã có
-- thể ghép cặp, nhưng match candidate cho chúng chưa tồn tại. Gọi lại detect cho các hồ sơ
-- liên quan để sinh candidate:
--   POST /api/v1/admin/profile-match-candidates/detect/{masterProfileId}
-- Tra danh sách hồ sơ nên detect lại (những hồ sơ dùng chung SĐT sau khi chuẩn hoá):
--   SELECT phone, count(*), array_agg(id)
--     FROM master_profiles
--    WHERE phone IS NOT NULL AND status NOT IN (3, 5)
--    GROUP BY phone HAVING count(*) > 1
--    ORDER BY count(*) DESC;
-- =====================================================================
