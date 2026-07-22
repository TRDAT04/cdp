# CDP VNPost — Bổ sung field API theo UI mockup (changelog)

**Ngày:** 2026-07-22 · **Hệ thống:** Customer Profile / CDP VNPost · **Trạng thái:** code xong, đã build/test cục bộ tab Hành vi số (các tab khác chờ verify)

> Mục tiêu chung: bổ sung/định hình lại **cấu trúc field** của các API profile để FE ghép đúng giao diện mockup. Nguyên tắc xuyên suốt: **ưu tiên đúng shape trước**, field nào chưa có nguồn dữ liệu để `null`/rỗng, **không** viết logic tính toán nghiệp vụ mới.

---

## 1. Tab "Tổng quan" — thêm `transactionRoles` (placeholder)

**API:** `ProfileOverviewResponse` (tab Tổng quan).

- Thêm field `transactionRoles` (object) phục vụ badge "Vai trò giao dịch" + 2 số liệu "X lần Người gửi / Y lần Người nhận".
- Tạo DTO mới `TransactionRolesResponse` (theo pattern `ProfileIdentitiesResponse`):
  ```json
  "transactionRoles": {
      "primaryRole": null,
      "roles": [],
      "senderCount": null,
      "receiverCount": null
  }
  ```
- **Placeholder** — set cứng giá trị mặc định trong assembler (`buildTransactionRolesPlaceholder()`), KHÔNG tính toán.
- Có comment `// TODO`: chờ field sender/receiver trong event `createOrder`.
- Chỉ đụng `ProfileOverviewResponse` + assembler; **không** đụng `ProfileDetailResponse`/`ProfileListItemResponse`.

**File:**
- `profile/dto/query/TransactionRolesResponse.java` (mới)
- `profile/dto/query/ProfileOverviewResponse.java`
- `profile/assembler/ProfileDetailAssembler.java` (`assembleOverview`)

---

## 2. Tab "Hồ sơ đa nguồn" — đổi shape `sources` + `rows`→`groups`

**API:** `GET` multi-source (`ProfileMultiSourceComparisonResponse`, `assembleMultiSource`).

### Thay đổi cấu trúc (BREAKING)
- `sources`: `List<String>` → `List<SourceInfo>` = `{ code, sourceCustomerId }`.
  - `sourceCustomerId` lấy từ `profile_source_records` (ưu tiên) → fallback `profile_identity_links`.
  - **Loại nguồn không có data** khỏi mảng (quyết định đã chốt với user). Thứ tự hiển thị theo mockup: `CAS, CRM, MYVNPOST, POSTID, PNS_DINGDONG, PAYPOST`; nguồn ngoài danh mục nhưng có data thật vẫn giữ (xếp cuối, sort A-Z).
- `rows` (phẳng) → `groups`: `List<Group>` với `Group = { groupName, rows }`. Giữ nguyên `Row` + `SourceValue` (logic `different` không đổi).

### Nhóm & row
- **ĐỊNH DANH**: `fullName, phone, email, identityNoOrTaxCode, gender, dateOfBirth, postId`.
  - `postId`: lấy per-nguồn từ `profile_identity_links` (identity_type = POST_ID, fallback nguồn POSTID).
  - `identityNoOrTaxCode` (label "CCCD/MST"): có CCCD → hiển thị CCCD (đã che mask), không có CCCD nhưng có MST → hiển thị MST. Áp cho cả masterValue lẫn từng nguồn.
- **TÀI CHÍNH/HỢP ĐỒNG** (nhóm mới):
  - `taxCode` (Mã số thuế): lấy thật từ `master_profiles.tax_code`.
  - `contract` (Hợp đồng): `null` mọi nguồn — TODO: chưa xác định nguồn dữ liệu.
  - `debt` (Công nợ): `null` mọi nguồn — TODO: chưa xác định nguồn dữ liệu.
- Service nạp thêm `links` + `records` để truyền vào assembler (chữ ký interface `ProfileQueryService` KHÔNG đổi).
- Xóa `PROPERTY_LABELS` (không còn dùng), dọn import (`TreeSet`→`Set/LinkedHashSet/HashMap`).

**File:**
- `profile/dto/query/ProfileMultiSourceComparisonResponse.java`
- `profile/assembler/ProfileDetailAssembler.java` (`assembleMultiSource` + helpers)
- `profile/service/ProfileQueryServiceImpl.java` (`getProfileMultiSource`)

**⚠ Cần chốt:** mã nguồn `PNS_DINGDONG` (mockup) không có trong enum `ProfileSourceSystemCode` (chỉ có `POSTID/PAYPOST/CAS/CRM/MYVNPOST`, `SOURCE_PRIORITY` dùng `PNS`). Vì "loại nguồn không data", nếu DB lưu `PNS` thì cột ra `PNS`, còn `PNS_DINGDONG` không xuất hiện.

---

## 3. Tab "Hành vi số" — thêm `lastCampaignResponse`

**API:** `ProfileDigitalBehaviorResponse` (`assembleBehavior`).

- Thêm field `lastCampaignResponse` + inner class `LastCampaignResponse { campaignCode, channel, occurredAt }`.
  ```json
  "lastCampaignResponse": {
      "campaignCode": "SALE06",
      "channel": "EMAIL",
      "occurredAt": "2026-06-10T16:00:00"
  }
  ```
- Logic: lấy **event `campaignResponse` gần nhất** (dùng lại `latestEvent`); `campaignCode` từ property, `channel` từ property `channel` (fallback `sourceSystem`), `occurredAt` từ event. Không có event → object `null`.
- Thêm constant trong `CustomerEventDerivations`: `EVENT_CAMPAIGN_RESPONSE="campaignResponse"`, `PROP_CAMPAIGN_CODE`, `PROP_CHANNEL`.
- Thêm nhãn timeline "Phản hồi campaign".

**Trạng thái:** đã build + test — profile test trả `lastCampaignResponse: null` (chưa có event campaign nào), các field cũ nguyên vẹn.

**File:**
- `profile/dto/query/ProfileDigitalBehaviorResponse.java`
- `profile/assembler/CustomerEventDerivations.java`
- `profile/assembler/ProfileDetailAssembler.java` (`assembleBehavior`, `mapEventTypeText`)

**⚠ Cần chốt:** tên `eventType="campaignResponse"` + property `campaignCode`/`channel` là **đặt tạm**. Khi chốt tên thật với nguồn, chỉ sửa 3 constant trong `CustomerEventDerivations`.

---

## 4. API "Mảng dịch vụ" — shape CHUNG (base + `extra`), bỏ `pendingFields`

**API:** `GET /api/v1/customers/{id}/service-lines` (`ProfileServiceLinesResponse`, `assembleServiceLines`).

### Thay đổi cấu trúc (BREAKING)
- **Xóa hẳn** `pendingFields` và `codTotal`.
- Dùng CHUNG 1 shape (base) cho cả 7 mảng; field đặc thù đặt trong object `extra`.
- `ServiceLineBlock` (base) thêm: `systemsUsed`, `successDeliveryRate`, `returnRate`, `avgDeliveryDays`, `cod` (object), `contributionBySource`, `signal`, `extra`.
- Inner class mới: `Cod { total, collected, outstanding, reconciliationStatus }`, `ContributionBySource { source, role, orderCount, codContribution }`.

### Logic
- **Giữ nguyên** cách tính `totalRevenue / totalOrders / avgOrdersPerMonth` (áp mọi mảng active, kể cả TMĐT — map ý nghĩa: totalRevenue = "DT qua sàn 12T", totalOrders = "đơn giao chặng cuối", returnRate = "tỷ lệ hoàn TMĐT").
- `cod`: chỉ **BCCP/TCBC** có object (`total` = tổng COD tính được, 3 field còn lại `null`); mảng khác `cod = null`.
- `contributionBySource`: chỉ **BCCP/TCBC** & active → group theo `sourceSystem` (`orderCount` + `codContribution`); `role = null`. Còn lại `[]`.
- `systemsUsed`: distinct `sourceSystem` của event trong mảng (rỗng nếu "Chưa dùng").
- `successDeliveryRate / returnRate / avgDeliveryDays / signal`: `null` (chưa có nguồn).
- `extra` (dùng `LinkedHashMap`, cho phép value null — **không** dùng `Map.of`):
  - TCBC: `{ mainChannel, topTransactionType }` (null)
  - LOGISTICS: `{ activeWarehouseCount, fulfillmentVolume, deliverySla, currentStockSku, onTimeDeliveryRate }` (null)
  - TMĐT: `{ gmv, onlineShopCount, mainPlatforms }` (null)
  - BCCP/PPBL/HCC/MVNO: `{}`

**File:**
- `profile/dto/query/ProfileServiceLinesResponse.java`
- `profile/assembler/ProfileDetailAssembler.java` (`assembleServiceLines`, `buildServiceLineExtra`, `ServiceLineAgg`/`SourceContribution`)

**⚠ Cần chốt:** `systemsUsed` hiện **derive từ `sourceSystem` của event thật** (không phải list tĩnh trong mockup như `["MPITS","CAS","PNS/DingDong"]`). Nếu muốn danh mục hệ thống cố định theo mảng → cần đổi sang static map.

---

## Ghi chú chung / rủi ro

- **Serializer:** project KHÔNG bật `NON_NULL` → field `null` và object/map rỗng vẫn xuất hiện đủ trong JSON (đã kiểm chứng thực tế ở tab Hành vi số).
- **Breaking với FE:** task 2 và 4 đổi shape (không chỉ thêm field). FE phải bind lại theo cấu trúc mới:
  - multi-source: `sources` là object, `rows`→`groups`.
  - service-lines: bỏ `codTotal`/`pendingFields`, thêm base fields + `extra`.
- **Build:** task 1/2/4 chưa chạy `mvn compile` trên máy này (môi trường bị chặn chạy Maven) — cần verify trước commit. Task 3 đã chạy OK.
- **Các field placeholder chờ nguồn dữ liệu:** `transactionRoles.*`, `contract`, `debt`, `cod.collected/outstanding/reconciliationStatus`, `contributionBySource.role`, `signal`, và toàn bộ `extra` — đều đã đánh dấu để bổ sung logic sau.
