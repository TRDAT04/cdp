# CDP VNPost — Tính năng "Hoạt động theo mảng dịch vụ chính" (changelog)

**Ngày:** 2026-07-22 · **Hệ thống:** Customer Profile / CDP VNPost · **Trạng thái:** code xong (chưa xác nhận `mvn compile` trên máy dev)

---

## 1. Mục tiêu
Hiển thị hoạt động của khách hàng theo **7 mảng dịch vụ chính**: BCCP, TCBC, PPBL, HCC, Logistics, TMĐT, MVNO.

Nguyên tắc (theo yêu cầu — data demo, ít thay đổi):
- Dùng **static mapping trong code**, **KHÔNG** tạo bảng/migration DB.
- Ánh xạ `serviceCode` → `ServiceLine` tại **thời điểm query** (không đụng luồng ingestion, không thêm cột).
- Metric chưa có nguồn dữ liệu → để `null` + liệt kê trong `pendingFields` (không bịa số).
- **KHÔNG** xử lý "vai trò" (người gửi/nhận) và "Tín hiệu/Rủi ro" ở bước này.

## 2. Nguồn dữ liệu
Suy diễn từ bảng `customer_events`:
- Chỉ event `event_type = createOrder`.
- Đọc từ `properties` (jsonb): `serviceCode`, `amount`, `paymentMethod` (`orderId`, `orderStatus` dùng ở nơi khác).
- Cửa sổ thời gian: **12 tháng** gần nhất (`occurredAt >= now - 12 tháng`).
- Điều kiện match: event chỉ gắn vào profile khi có `ProfileSourceRecord` cùng `(sourceSystem, sourceCustomerId)` trỏ tới `MasterProfile` (nếu không → `UNMATCHED`, không tính).

## 3. API
`GET /api/v1/admin/profiles/{id}/service-lines` — `{id}` = masterProfileId.
Đặt theo đúng quy ước các tab profile khác (`/overview`, `/behavior`, `/summary`…), **không** dùng `/api/v1/customers/...` như spec gốc (để nhất quán toàn dự án).

Trả về **đủ 7 mảng** (kể cả mảng "Chưa dùng"). Mỗi mảng:
- `code`, `name`, `active`, `statusText` ("Đang dùng"/"Chưa dùng").
- Metric tính được: `totalRevenue` (Σ amount), `totalOrders` (đếm đơn), `avgOrdersPerMonth` (totalOrders/12), `codTotal` (Σ amount với `paymentMethod=COD`).
- `pendingFields`: danh sách field spec yêu cầu nhưng **chưa có nguồn dữ liệu** (để null ở demo).

Mảng "Chưa dùng": 4 metric số để `null`, chỉ có `active=false` + `statusText`.

## 4. Nhất quán giữa các API (điểm quan trọng)
Trước đây `search` và `summary` hiển thị `serviceCode` **thô** (vd `EMS`). Đã sửa để **cả 3 API dùng chung 1 nguồn** ánh xạ:
- `GET /api/v1/admin/profiles` (search/list) → field `serviceLines` giờ trả **mã mảng** (`BCCP`…).
- `GET /api/v1/admin/profiles/{id}/summary` → tương tự.
- `GET /api/v1/admin/profiles/{id}/service-lines` → như mục 3.

**Chưa map (cố ý):** `ProfileDigitalBehaviorResponse.RecentOrder.serviceCode` (tab Hành vi số) vẫn giữ `serviceCode` gốc của **đơn hàng cụ thể** — đây là thuộc tính của đơn, không phải "mảng dịch vụ".

## 5. File THÊM MỚI

| File | Chức năng |
|------|-----------|
| `profile/enums/ServiceLine.java` | Enum 7 mảng dịch vụ (BCCP, TCBC, PPBL, HCC, LOGISTICS, TMDT, MVNO) + nhãn tiếng Việt `getLabel()` (nhãn TẠM). |
| `profile/service/serviceline/ServiceCodeMapper.java` | `Map<String,ServiceLine>` cứng + `resolve(serviceCode)` (case-insensitive, trim; trả `null` + log WARN khi gặp mã chưa map). **Comment rõ: data TẠM — cần thay bằng mã thật từ CAS/MPITS/PayPost/TMS-WMS.** |
| `profile/dto/query/ProfileServiceLinesResponse.java` | DTO response: `masterProfileId`, `monthsWindow`, `serviceLines[7]` (mỗi block: code/name/active/statusText + metric + `pendingFields`). |
| `test-data/service-line-event-samples.json` | 4 event `createOrder` mẫu (KHO_VAN→Logistics, POS_RETAIL→PPBL, SIM_DATA_MVNO→MVNO, THU_HO_COD→TCBC) + ghi rõ điều kiện match. Dùng để demo nhiều mảng "Đang dùng". |
| `docs/service-line-feature.md` | Tài liệu này. |

## 6. File CHỈNH SỬA (chỉ thêm, không phá logic cũ)

| File | Thay đổi |
|------|----------|
| `profile/assembler/CustomerEventDerivations.java` | (1) Thêm hằng `PROP_PAYMENT_METHOD = "paymentMethod"`. (2) `resolveTopServiceLines()` giờ ánh xạ serviceCode qua `ServiceCodeMapper` → trả mã mảng (`BCCP`…) thay vì thô (`EMS`) → **sửa 1 chỗ, fix cả search + summary**. |
| `profile/assembler/ProfileDetailAssembler.java` | Thêm `assembleServiceLines(masterProfileId, events)`: lọc createOrder trong 12 tháng, resolve serviceLine, gom nhóm, tính totalRevenue/totalOrders/avgOrdersPerMonth/codTotal, luôn xuất đủ 7 mảng. Kèm hằng `SERVICE_LINE_MONTHS_WINDOW=12` và map `PENDING_FIELDS`. |
| `profile/service/ProfileQueryService.java` | Thêm chữ ký `getProfileServiceLines(Long id)`. |
| `profile/service/ProfileQueryServiceImpl.java` | Thêm impl `getProfileServiceLines()`: nạp events qua `findTop50ByMasterProfileIdOrderByOccurredAtDesc`, gọi assembler. |
| `profile/controller/MasterProfileController.java` | Thêm endpoint `GET /{id}/service-lines`. |

## 7. CHƯA làm / để lại cho sau
- **Mapping serviceCode → mảng là data TẠM** trong `ServiceCodeMapper` — thay bằng mã dịch vụ thật khi nghiệp vụ xác nhận.
- **Metric riêng từng mảng** (tỷ lệ phát/hoàn, thời gian giao, SLA, tồn kho SKU, điểm kho, COD đã/chưa thu/đối soát, bảng đóng góp theo nguồn, kênh chính, loại GD…) — chưa có nguồn dữ liệu → để `null` + liệt kê ở `pendingFields`.
- **"Vai trò" và "Tín hiệu/Rủi ro"** — cố ý chưa làm.
- **UI 7 tab** — thuộc project frontend riêng, repo này là backend thuần.
- **Xác nhận build** — cần chạy `mvn -o compile` trên máy dev trước khi coi là hoàn tất.

## 8. Cách demo nhanh
1. Đảm bảo profile mục tiêu (vd id=69) đã có `ProfileSourceRecord` khớp `(sourceSystem, sourceCustomerId)`.
2. Gửi lần lượt các body trong `test-data/service-line-event-samples.json` tới `POST /api/v1/admin/customer-events/send` (thay `sourceSystem`/`sourceCustomerId` nếu profile dùng cặp khác).
3. Gọi `GET /api/v1/admin/profiles/{id}/service-lines` → thấy 5/7 mảng "Đang dùng" (BCCP + Logistics/PPBL/MVNO/TCBC).
