# CDP VNPost — Tính năng "Điểm số & Phân khúc" (changelog)

**Ngày:** 2026-07-23 · **Hệ thống:** Customer Profile / CDP VNPost · **Trạng thái:** code xong, đã chạy được endpoint trên máy dev (sau khi fix cast native query).

---

## 1. Mục tiêu
Cung cấp 1 API tổng hợp các điểm số/khách hàng cho tab **"Điểm số & Phân khúc"**:
`rfm` (phân khúc RFM), `clv` (giá trị vòng đời), `churnScore` (điểm rời bỏ), `engagementScore` (điểm gắn kết), `codRiskScore`, `fraudScore`.

Nguyên tắc (theo yêu cầu):
- **RFM dùng công thức CHUẨN ngành** (percentile/quintile qua `NTILE(5)`), **không** dùng ngưỡng tự đặt.
- Tái sử dụng logic sẵn có: `assembleServiceLines` (CLV) và `assembleBehavior` (churn/engagement), **không viết lại**.
- **KHÔNG** tạo entity/bảng mới (chỉ thêm 1 native query cho RFM cross-customer).
- Field `null` (`codRiskScore`, `fraudScore`, và `churnScore` khi khách chưa mua) **VẪN xuất hiện** trong JSON.
- Chạy được kể cả khi hệ thống chỉ có ít profile (3-5) — không throw, không cần "đủ số lượng mới chạy".

## 2. API
`GET /api/v1/admin/profiles/{id}/scoring` — `{id}` = `master_profiles.id`.
Đặt theo đúng quy ước các tab profile khác (`/summary`, `/behavior`, `/service-lines`…), **không** dùng `/api/v1/customers/...` như spec gốc (để nhất quán toàn dự án).

Response (bọc trong `MethodResult.data`):
```json
{
    "rfm": { "segment": "Champions", "recencyScore": 5, "frequencyScore": 4, "monetaryScore": 5 },
    "clv": 68700000,
    "churnScore": 18,
    "engagementScore": 60,
    "codRiskScore": null,
    "fraudScore": null
}
```

## 3. Công thức từng chỉ số

### 3.1 RFM — percentile/quintile (5 = tốt nhất)
- **Chỉ số thô** (tính cho MỌI `master_profiles` từ event `createOrder`):
  - `recency_days` = số ngày từ đơn gần nhất (mọi thời điểm) đến hiện tại; `NULL` nếu chưa từng mua.
  - `frequency` = số đơn trong **12 tháng**.
  - `monetary` = tổng `properties->>'amount'` trong **12 tháng**.
- **Xếp hạng** bằng `NTILE(5)` trên toàn bộ khách:
  - Recency: `ORDER BY recency_days DESC NULLS FIRST` → mua gần đây (ngày nhỏ) rơi vào bucket cao. Khách chưa mua (NULL) → bucket 1 (recency tệ nhất).
  - Frequency / Monetary: `ORDER BY ... ASC` → giá trị lớn rơi vào bucket cao.
- **Segment** (đánh giá theo thứ tự, first-match):

  | Điều kiện | Segment |
  |-----------|---------|
  | R≥4 và F≥4 và M≥4 | Champions |
  | R≥3 và F≥3 và M≥3 | Loyal Customers |
  | R≥4 và F≤2 | New Customers |
  | R≤2 và F≥4 và M≥4 | At Risk |
  | R≤2 và F≤2 và M≤2 | Lost |
  | R≥3 và F≤2 và M≥4 | Big Spenders |
  | còn lại | Regular |

> ⚠️ **Điểm khác spec gốc (cố ý):** SQL mẫu trong spec dùng `recency ASC`, `frequency/monetary DESC`. Với `NTILE`, dòng đứng **đầu** ORDER BY nhận bucket **1**, nên hướng đó cho ra **1 = tốt nhất** — trái với JSON mẫu (`5,4,5 → Champions`), segment mapping (R≥4 = tốt) và RFM chuẩn ngành (5 = tốt). Đã **đảo hướng ORDER BY** để đạt **5 = tốt nhất**, khớp cả output mẫu lẫn segment.

> ℹ️ Với ít profile (3-5), `NTILE(5)` không lỗi — chỉ là các bucket cao có thể trống, nên điểm thực tế có thể chỉ nằm trong 1..3.

### 3.2 CLV
`CLV = Σ totalRevenue` của 7 mảng dịch vụ — gọi `assembleServiceLines(id, events)` rồi cộng `totalRevenue` các block (bỏ qua `null`). Cửa sổ 12 tháng theo đúng logic service-lines.

### 3.3 Churn score
```
daysSinceLastOrder = số ngày từ recentOrder.occurredAt đến hiện tại
churnScore = ROUND(MIN(daysSinceLastOrder / 90.0 * 100, 100))
```
Nếu khách **chưa có** `recentOrder` → `churnScore = null` (KHÔNG gán 100).

### 3.4 Engagement score (rule-based, tối đa 100)
+20 cho mỗi điều kiện; thiếu dữ liệu (null) → 0 điểm cho điều kiện đó (không bỏ qua, không chuẩn hóa lại thang):
- `lastLoginAt` trong 7 ngày qua
- `sessionsLast30Days > 0`
- `channelsInteracted.size() >= 3`
- có `recentOrder` trong 30 ngày qua
- `lastCampaignResponse` trong 30 ngày qua

### 3.5 COD risk & Fraud
`codRiskScore = null`, `fraudScore = null` — **CHƯA implement** (COD: chưa join được `complaintCreated.orderId` với đơn hàng; Fraud: cần đối chiếu pattern nhiều khách, không tính được từ 1 profile).

## 4. Nguồn dữ liệu
- RFM: native query trực tiếp trên `master_profiles` LEFT JOIN `customer_events` (`event_type='createOrder'`), đọc `occurred_at` và `properties->>'amount'`.
- CLV / churn / engagement: `customer_events` của riêng profile (fetch uncapped, mới nhất trước) → 2 assembler sẵn có.

## 5. File THÊM MỚI

| File | Chức năng |
|------|-----------|
| `profile/dto/query/ProfileScoringResponse.java` | DTO response: `rfm{segment,recencyScore,frequencyScore,monetaryScore}`, `clv`, `churnScore`, `engagementScore`, `codRiskScore`, `fraudScore`. Gắn `@JsonInclude(ALWAYS)` để field null vẫn xuất hiện. |
| `profile/service/ScoringService.java` | Interface: `ProfileScoringResponse getProfileScoring(Long id)`. |
| `profile/service/ScoringServiceImpl.java` | Logic: `computeRfm` + `resolveSegment`, `computeClv` (reuse `assembleServiceLines`), `computeChurnScore`, `computeEngagementScore` (reuse `assembleBehavior`). |
| `docs/scoring-segment-feature.md` | Tài liệu này. |

## 6. File CHỈNH SỬA (chỉ thêm, không phá logic cũ)

| File | Thay đổi |
|------|----------|
| `customer_event/repository/CustomerEventRepository.java` | (1) Thêm `findByMasterProfileIdOrderByOccurredAtDesc` (uncapped — bản top-50 không đủ cho CLV 12 tháng). (2) Thêm native query `findRfmScores(profileId, now, windowStart)` dùng `NTILE(5)`. |
| `profile/controller/MasterProfileController.java` | Inject `ScoringService` vào constructor + endpoint `GET /{id}/scoring`. |

## 7. Lưu ý kỹ thuật quan trọng
- **Native query & cú pháp cast:** không dùng `::numeric` trong native query của Hibernate — dấu `::` bị parser hiểu là named parameter (`:numeric`) → lỗi `syntax error at or near ":"`. Đã dùng `cast(ce.properties ->> 'amount' as numeric)`. Đây là native query đầu tiên có `NTILE`/window function trong dự án.
- **CLV giới hạn theo `ServiceCodeMapper`:** doanh thu từ `serviceCode` chưa được map sẽ không tính vào CLV (giới hạn sẵn có của service-lines, không phải bug mới).
- **`now` không nhất quán tuyệt đối:** RFM query và churn/engagement dùng `LocalDateTime.now()` lấy 1 lần trong `getProfileScoring` — chênh lệch giữa các phép tính không đáng kể.

## 8. CHƯA làm / để lại cho sau
- **COD risk & Fraud score** — chưa có đủ dữ liệu/logic (xem 3.5).
- **Test tự động** — chưa viết.
- **Xác nhận build đầy đủ** — endpoint đã chạy được trên dev; nên chạy `mvn -o compile` + smoke test với nhiều profile trước khi coi là hoàn tất.
- **UI tab "Điểm số & Phân khúc"** — thuộc project frontend riêng.
