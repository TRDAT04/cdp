# CDP VNPost — Luồng dữ liệu đầu vào (Ingestion → Identity Resolution → Unomi)

**Ngày:** 2026-07-31 · **Hệ thống:** Profile Ingestion / Identity Resolution · **Trạng thái:** mô tả code hiện tại trên branch `main` (đã `mvn compile` pass)

Tài liệu này mô tả **luồng đi thực tế của dữ liệu trong code**, không phải thiết kế mong muốn. Mọi mục đều dẫn tới class/method cụ thể để đối chiếu khi code đổi.

---

## 1. Hai đường vào (hoàn toàn tách biệt)

```
┌─ REST (chỉ để test): POST /api/v1/test/profile-ingestion/send
│      ProfileIngestionController → ProfileIngestionProducer ──┐
│                                                              ▼
│                                     Kafka topic: cdp.profile.events
│                                                              │
│                                     ProfileEventConsumer (manual ack)
│                                                              ▼
│                                     ProfileIngestionServiceImpl.process()
│                                              ← PIPELINE HỒ SƠ (mục 2)
│
└─ Kafka topic: cdp.customer.events
        CustomerEventConsumer → CustomerEventServiceImpl.process()
                                              ← LUỒNG HÀNH VI (mục 6)
```

| | `cdp.profile.events` | `cdp.customer.events` |
|---|---|---|
| Nội dung | dữ liệu **hồ sơ** khách hàng | **event hành vi** (createOrder, pageView…) |
| Qua identity resolution | ✅ | ❌ |
| Tạo / gộp `master_profiles` | ✅ | ❌ |
| Phụ thuộc | — | **phụ thuộc** luồng hồ sơ đã chạy trước |

Controller REST **không** gọi service trực tiếp — nó chỉ publish vào Kafka, nên đường test và đường thật đi cùng một code path.

---

## 2. Pipeline hồ sơ — 7 bước trong `ProfileIngestionServiceImpl.process()`

### B1. Lưu raw trước mọi thứ
`profile_source_records` với `mergeStatus=0` (PENDING), giữ nguyên `rawPayload`. Ghi **trước** khi xử lý nên mọi message đều có vết, kể cả khi crash ngay sau đó.

### B2. Normalize — `ProfileNormalizationService.normalize()`
Đọc payload dạng `Map<String,Object>` → `NormalizedProfileData` + `normalizedPayload` (lưu lại DB):

| Field | Xử lý |
|---|---|
| `fullName` | trim + gom nhiều space thành một |
| `phone` | bỏ space và dash, **giữ nguyên dấu `+`** |
| `email` | trim + lowercase |
| `identityNo`, `taxCode` | trim + bỏ hết space |
| `dateOfBirth` | thử 3 format: `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy` |
| `lastVisitAt` | thử 3 format datetime |
| Định danh liên nguồn | `postId`, `crmId`, `khlCode`, `appUserId`, `deviceId`, `cookieId`, `paymentId` — chỉ trim |

### B3. `resolveIdentityKey()`
Chọn khóa đại diện theo thứ tự `identityNo > phone > email > sourceCustomerId`, ghi vào source record. **Chỉ dùng để tra cứu/audit — không tham gia quyết định merge.**

### B4. Sinh candidate — `ProfileMatchingService.findCandidateProfiles()`
Quét 6 nhánh, gom vào `LinkedHashSet` chống trùng:

| # | Khóa | Tìm ở |
|---|---|---|
| 1 | `identityNo` | `master_profiles` |
| 2 | `phone` | `master_profiles` + `identity_links` (type `PHONE`, status=1) |
| 3 | `email` | `master_profiles` + `identity_links` (type `EMAIL`, status=1) |
| 4 | `sourceSystem` + `sourceCustomerId` | `identity_links` |
| 5 | `taxCode` | `master_profiles` |
| 6 | `KHL_CODE` / `CRM_ID` / `POST_ID` / `APP_USER_ID` / `PAYMENT_ID` | `identity_links` |

Cố tình **không** dùng `DEVICE_ID` / `COOKIE_ID` — để dành Probabilistic Matching, tránh false-positive khi auto-merge.

### B5. Quyết định — `ProfileMergeDecisionService.decide()`

```
source system không hợp lệ                        → REJECT
không có identityNo/phone/email/sourceCustomerId  → REJECT
0 candidate                                       → CREATE_NEW_PROFILE
>1 candidate                                      → CONFLICT
== 1 candidate → chain deterministic-first:
    FP1  đã link (sourceSystem + sourceCustomerId, status=1)  → AUTO_MERGE
    FP2  identityNo lệch → NEED_REVIEW  |  khớp → AUTO_MERGE
    FP2b taxCode  lệch → NEED_REVIEW
         taxCode khớp + tên ≥75%  → AUTO_MERGE
         taxCode khớp + tên <75%  → NEED_REVIEW
    FP2c khớp typed id (KHL/CRM/Post/AppUser/Payment) → AUTO_MERGE
    FP3  eventType=PROFILE_ENRICHED + khớp phone|email → AUTO_MERGE
    ─── fallback probabilistic: scoreService.calculate() ───
         identityConflict = true → NEED_REVIEW
         score ≥ 95              → AUTO_MERGE
         score ≥ 70              → CREATE_MATCH_CANDIDATE
         score < 70              → CREATE_NEW_PROFILE
```

**Nguyên tắc:** khóa mạnh (CCCD, MST, PostID, CRM ID, mã KHL) quyết định trước; điểm cộng dồn (tên gần đúng, ngày sinh, tỉnh, đơn vị) chỉ là **fallback** khi không có khóa mạnh nào so được.

### B6. Thực thi — `switch (decision)` → `ProfileMergeExecutorService`

| Decision | Làm gì | `mergeStatus` | Unomi | Detect candidate |
|---|---|---|---|---|
| `CREATE_NEW_PROFILE` | tạo `MasterProfile` (`MP_yyyyMMdd_XXXXXXXX`) + identity links (primary + enrichment) + attribute values (`isSelected=true`) + change logs | 1 MERGED | ✅ | ✅ |
| `AUTO_MERGE` | ghi attribute values (`isSelected=false`), rồi **rule engine từng field** (`ProfileMergeEngineService.shouldOverwrite`) quyết định ghi đè; mỗi lần ghi đè → 1 change log + đổi `isSelected` | 1 MERGED | ✅ | ✅ |
| `CREATE_MATCH_CANDIDATE` | `createProfileForReview()` tạo hồ sơ MỚI (không sync, không đánh MERGED) → `createCandidateBetweenProfiles()` tạo cặp PENDING | 4 NEED_REVIEW | ❌ | ❌ |
| `NEED_REVIEW` | **không** sửa master profile; ghi attribute values `isSelected=false` + `profile_merge_conflicts` cho phone/email/identityNo lệch (`fullName` cố tình loại — khác dấu/format không phải xung đột) | 4 NEED_REVIEW | ❌ | ❌ |
| `CONFLICT` | 1 dòng conflict `propertyName=PROFILE_MATCH` liệt kê id các candidate; **không chạm** hồ sơ nào | 2 CONFLICT | ❌ | ❌ |
| `REJECT` | chỉ ghi lý do vào `errorMessage` | 3 REJECTED | ❌ | ❌ |

### B7. Exception
`catch` ngoài cùng đánh `mergeStatus=5` (ERROR) + `errorMessage` (cắt 2000 ký tự), **không rethrow** → consumer vẫn `acknowledge()`. Message **không retry**, chỉ còn vết trong source record.

---

## 3. Xử lý async sau commit

Chỉ 2 nhánh `CREATE_NEW_PROFILE` và `AUTO_MERGE` publish `ProfileMergedEvent`.
`ProfileMergedEventListener` chạy `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`:

1. `unomiService.syncProfileToUnomi()` → ghi `profile_unomi_sync_logs` (status 1 SUCCESS / 2 FAILED), set `syncedToUnomiAt`.
2. `matchCandidateService.detectAndCreateCandidatesForProfile()` → sinh candidate cho hàng đợi đối soát của admin. Lỗi chỉ `log.warn`, **không** ảnh hưởng kết quả ingestion.

> Listener này là **điểm nối duy nhất** giữa auto-merge của ingestion và hàng đợi đối soát của admin.

---

## 4. Deterministic-first cho luồng admin (`resolveMatch`)

**Bối cảnh:** `ProfileMatchScoreService` là additive nên hai hồ sơ trùng CCCD (hoặc trùng MST) hoàn toàn chỉ được 50/100 — không đạt ngưỡng auto-merge và bị xếp ngang một cặp chỉ trùng vài tín hiệu yếu. Luồng ingestion đã có deterministic-first sẵn (mục B5), nhưng luồng admin trước đây gọi thẳng `calculate()`.

**Đã thêm** `ProfileMatchCandidateServiceImpl.resolveMatch(left, right)` — luôn gọi `calculate()` trước để có đủ reasons, rồi:

| Điều kiện | Kết quả |
|---|---|
| CCCD hai bên đều có, **lệch** | trả nguyên kết quả probabilistic (`identityConflict=true` → `autoMergeRecommended` đã false) |
| CCCD hai bên đều có, **khớp** | score = **98**, `VERY_HIGH` |
| MST hai bên đều có, **lệch** | trả nguyên kết quả probabilistic |
| MST khớp + tên ≥75% (hoặc thiếu tên một bên) | score = **96**, `VERY_HIGH` |
| MST khớp + tên <75% | giữ score, **force** `autoMergeRecommended=false` |
| Không so được khóa mạnh nào | probabilistic thuần, không đổi |

**Quyết định thiết kế:**
- **98 / 96 thay vì 100** — để phân biệt với trường hợp điểm cộng dồn bị cap về 100. Nhìn score là biết candidate đến từ nhánh nào. CCCD > MST vì MST có thể bị dùng chung.
- **Giữ ĐỦ danh sách reason** (không rút còn 1 dòng) — màn đối chiếu cần thấy đủ bằng chứng, và `matchedKeys` ở màn nhóm lấy trực tiếp từ bảng `profile_match_reasons` nên rút bớt sẽ mất khoá khớp trên UI.
- **Nhánh xung đột không tự đặt điểm** — nếu trả score thấp cố định thì `createCandidate()` sẽ throw `SCORE_TOO_LOW` và `detectAndCreate` sẽ skip, làm **mất** candidate review hiện đang được tạo. Delegate xuống `calculate()` giữ đúng hành vi cũ.
- **`autoMergeRecommended = !identityConflict`** (không hard-code `true`) — thận trọng hơn ingestion một bậc ở đúng một case: **trùng CCCD nhưng lệch MST**, ingestion sẽ AUTO_MERGE còn admin thì không. Lý do: giữ invariant của scorer (`autoMerge ⇒ !conflict`) và **merge chưa có cơ chế unmerge**.
- **KHÔNG auto-merge ở luồng admin** — candidate luôn PENDING, admin bấm tay. `autoMergeRecommended` hiện chỉ được đọc ở `ProfileMergeDecisionService` (ingestion); admin flow không đọc, entity `ProfileMatchCandidate` cũng không có cột lưu cờ này.
- **Pool detect thêm `findByTaxCode`** — nếu không, nhánh deterministic MST **không bao giờ** được kích hoạt từ luồng detect (pool cũ chỉ có identityNo/phone/email).
- **Không đổi**: `ProfileMatchScoreService`, `ProfileMergeDecisionService`, `createCandidateBetweenProfiles()` (ingestion đã tự xử lý deterministic riêng trước khi gọi tới), ngưỡng 70, rule "+10 điểm", `persistCandidateWithReasons`, entity, response DTO.

**Side-effect vận hành:** cặp trùng CCCD/MST từng bị admin IGNORED/REJECTED ở score ~50 sẽ có score mới 96–98 → diff ≥10 → **được tạo lại một lần** sau khi deploy. Hàng đợi PENDING sẽ tăng ở lần chạy detect đầu tiên.

---

## 5. Luồng admin (`ProfileMatchCandidateController`)

Đọc từ `profile_match_candidates` mà pipeline trên đã sinh:

| Endpoint | Việc |
|---|---|
| `searchPendingGroups()` | màn "Đối soát định danh" — gom candidate PENDING theo hồ sơ gốc (native `UNION ALL` + `GROUP BY`, phân trang ở DB) |
| `getById()` / `search()` | màn đối chiếu 2 vế + danh sách reason (bằng chứng) |
| `createCandidate(l, r)` | admin tự ghép tay 2 hồ sơ — **cũng đi qua `resolveMatch()`** |
| `ignore()` / `reject()` | đóng candidate; muốn tạo lại thì score mới phải cao hơn **≥10 điểm** |
| `merge()` | gộp thật (xem dưới) |

`merge()` theo thứ tự: `mergeProfileData` (chỉ fill field trống) → copy identity links + attribute values → `reassignReferencedData` (re-point `customer_events` / `profile_source_records` / `profile_merge_conflicts` sang target) → source thành `status=3 MERGED` + `mergedIntoProfileId` → expire các candidate PENDING còn trỏ tới source → sync **cả** target và source lên Unomi (source để Unomi biết hồ sơ đã chết, tránh đếm trùng ở segment).

---

## 6. Luồng event hành vi (`cdp.customer.events`)

Ngắn hơn nhiều, **không** qua matching/merge:

1. `saveEvent()` — tra `profile_source_records` **mới nhất** theo `(sourceSystem, sourceCustomerId)` → lấy `masterProfileId` → load `MasterProfile`.
2. Match được → lưu `customer_events` kèm `masterProfileId` / `profileCode`, `syncStatus=0` PENDING.
   Không match → lưu với `masterProfileId=null`, `syncStatus=3` UNMATCHED và **dừng** (không push Unomi).
3. `syncToUnomi()` — build `UnomiEventRequest` (`profileId = profileCode`); riêng `createOrder` bơm thêm `transactionDate` từ `occurredAt`; timeout 5s → `syncStatus` 1 SUCCESS / 2 FAILED.

---

## 7. Bảng chịu tác động

| Bảng | Ghi ở đâu |
|---|---|
| `profile_source_records` | B1 (raw) → B3 (identityKey, normalizedPayload) → B6 (mergeStatus, masterProfileId, processedAt) |
| `master_profiles` | `createNewProfile`, `autoMerge` (qua rule engine), `createProfileForReview`, admin `merge()` |
| `profile_identity_links` | `createIdentityLink` (primary) + `createEnrichmentIdentityLinks` (7 typed id, upsert không trùng) |
| `profile_attribute_values` | mọi nhánh có ghi dữ liệu; `isSelected` quyết định giá trị nào là "giá trị vàng" |
| `profile_change_logs` | tạo mới (all field), rule engine cho phép ghi đè (per-field), CONFLICT_DETECTED, ADMIN_MERGE |
| `profile_merge_conflicts` | `CONFLICT` (`PROFILE_MATCH`), `NEED_REVIEW` (phone/email/identityNo) |
| `profile_match_candidates` + `profile_match_reasons` | `CREATE_MATCH_CANDIDATE` (ingestion), `detectAndCreateCandidatesForProfile` (async), `createCandidate` (admin) |
| `profile_merge_requests` | admin `merge()` (status 3 COMPLETED) |
| `profile_unomi_sync_logs` | `ProfileMergedEventListener`, admin `merge()` |
| `customer_events` | luồng hành vi |

---

## 8. Gap / rủi ro đã xác định (CHƯA sửa)

1. **`findById(null)` sẽ nổ ở `CustomerEventServiceImpl.saveEvent()`.** Nhánh `CONFLICT` và `REJECT` để `masterProfileId = null` trên source record, nhưng `saveEvent()` chỉ check `sourceRecord != null` rồi gọi thẳng `findById(sourceRecord.getMasterProfileId())` → Spring Data throw `InvalidDataAccessApiUsageException`. `CustomerEventConsumer` bắt exception rồi **vẫn `acknowledge()`** → event **mất luôn**, không có dòng nào trong `customer_events` (đáng lẽ phải là UNMATCHED). → Cần check null trước `findById`.

2. **Hai bộ normalize song song.** `ProfileNormalizationService.normalizePhone` giữ `+84...`, còn `IdentityUtils.normalizePhone` convert `84 → 0`. Pool candidate (cả `ProfileMatchingService` và `detectAndCreateCandidatesForProfile`) query bằng giá trị **chưa** qua `IdentityUtils` → `+84912xxx` và `0912xxx` không bao giờ tìm thấy nhau, dù scorer so sánh thì lại coi là khớp.

3. **`findByPhone` / `findByEmail` / `findByIdentityNo` / `findByTaxCode` trả `Optional`** — 3 hồ sơ trùng SĐT chỉ lấy được 1, thứ tự không xác định. Ảnh hưởng cả pool ingestion lẫn pool detect.

4. **Nhánh `CONFLICT` (>1 candidate) không sinh match candidate nào** — chỉ 1 dòng `profile_merge_conflicts`. Đúng là ca "nhiều hồ sơ trùng" nghiêm trọng nhất nhưng **không** xuất hiện ở màn "Đối soát định danh".

5. **`AUTO_MERGE` bỏ dữ liệu incoming nếu rule engine chặn** — chỉ còn ở `profile_attribute_values` với `isSelected=false`, **không có change log** (chỉ log ra file). Muốn truy "vì sao giá trị này không vào master" phải đọc log ứng dụng.

6. **Khóa mạnh PostID / KHL_CODE / CRM_ID / APP_USER_ID / PAYMENT_ID chưa có trong luồng admin.** `ProfileMergeDecisionService` đã xử lý qua `ProfileIdentityLinkRepository` (FP2c), `resolveMatch()` thì chưa — mới chỉ có CCCD và MST.

7. **Message không retry.** Cả 2 consumer đều `acknowledge()` trong `catch`. Không có DLQ. Recovery duy nhất là đọc `profile_source_records` (mergeStatus=5) rồi reprocess thủ công — và luồng `customer_events` thì không có cả vết đó (xem #1).

8. **`merge()` không có unmerge.** Đây là lý do các quyết định ở mục 4 nghiêng về phía thận trọng.

---

## 9. File tham chiếu

**Đường vào**
- `kafka/consumer/ProfileEventConsumer.java`, `kafka/consumer/CustomerEventConsumer.java`
- `ingestion/controller/ProfileIngestionController.java`, `ingestion/producer/ProfileIngestionProducerImpl.java`

**Pipeline hồ sơ**
- `ingestion/service/ProfileIngestionServiceImpl.java` — orchestrator 7 bước
- `ingestion/service/ProfileNormalizationService.java` — B2
- `ingestion/service/ProfileMatchingService.java` — B4
- `ingestion/service/ProfileMergeDecisionService.java` — B5
- `ingestion/service/ProfileMergeExecutorService.java` — B6
- `ingestion/enums/MergeDecision.java`

**Matching / merge**
- `profile/service/match/ProfileMatchScoreService.java` — additive scoring (không đổi)
- `profile/service/match/ProfileMatchCandidateServiceImpl.java` — `resolveMatch()` + admin flow
- `profile/service/ProfileMergeEngineService.java` — rule engine per-field
- `common/utils/IdentityUtils.java` — chuẩn hoá dùng chung + Levenshtein
- `profile/event/ProfileMergedEvent.java`, `profile/event/ProfileMergedEventListener.java`

**Luồng hành vi**
- `customer_event/service/CustomerEventServiceImpl.java`

---

## 10. Liên quan

- `docs/customer-profile-identity-changelog.md` — bổ sung field định danh + FP2b/FP2c/FP3 cho ingestion
- `docs/scoring-segment-feature.md` — scoring RFM/CLV/churn (khác hoàn toàn với match score ở tài liệu này)
