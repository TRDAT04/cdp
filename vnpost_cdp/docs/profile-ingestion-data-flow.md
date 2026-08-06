# CDP VNPost — Luồng dữ liệu đầu vào (Ingestion → Identity Resolution → Unomi)

**Cập nhật:** 2026-08-05 · **Hệ thống:** Profile Ingestion / Identity Resolution · **Trạng thái:** mô tả code hiện tại trên branch `main` (compile pass, 52/52 unit test pass — xem mục 9)

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
│                                              ← PIPELINE HỒ SƠ (mục 3)
│
└─ Kafka topic: cdp.customer.events
        CustomerEventConsumer → CustomerEventServiceImpl.process()
                                              ← LUỒNG HÀNH VI (mục 7)
```

| | `cdp.profile.events` | `cdp.customer.events` |
|---|---|---|
| Nội dung | dữ liệu **hồ sơ** khách hàng | **event hành vi** (createOrder, pageView…) |
| Qua identity resolution | ✅ | ❌ |
| Tạo / gộp `master_profiles` | ✅ | ❌ |
| Phụ thuộc | — | **phụ thuộc** luồng hồ sơ đã chạy trước |

Controller REST **không** gọi service trực tiếp — nó chỉ publish vào Kafka, nên đường test và đường thật đi cùng một code path.

---

## 2. Chuẩn hoá định danh — một bộ duy nhất

`common/utils/IdentityUtils.java` là bộ chuẩn hoá **duy nhất** của hệ thống. Đây là bất biến quan trọng nhất của toàn bộ tài liệu này:

> Mọi nơi **ghi** giá trị định danh vào `master_profiles` / `profile_identity_links`, và mọi nơi **truy vấn** hoặc **so sánh** chúng, đều phải đi qua `IdentityUtils`.

Nếu tồn tại hai bộ chuẩn hoá song song thì giá trị được LƯU sẽ khác giá trị dùng để TRUY VẤN, và hai hồ sơ của cùng một người **không bao giờ được ghép cặp để so** — dù nếu được ghép thì scorer lại kết luận là khớp. Đó chính là lỗi từng khiến toàn bộ việc khớp theo SĐT vô hiệu (mục 8.2).

| Hàm | Dạng chuẩn | Ghi chú |
|---|---|---|
| `normalizePhone` | dạng nội địa có 0 đầu: `0912345678` | `+84912345678`, `84912345678`, `0084912345678`, `+84 912.345.678` đều về cùng dạng. Trả `null` nếu không còn chữ số nào — **không** trả chuỗi rỗng, vì rỗng lọt vào DB sẽ khiến `findByPhone("")` khớp hàng loạt |
| `normalizeEmail` | trim + lowercase | |
| `normalizeIdentityNo` | bỏ **mọi** khoảng trắng + in hoa | Dùng cho cả CCCD và MST. **KHÔNG** bỏ dấu gạch ngang: với MST, `0101234567-001` là chi nhánh còn `0101234567` là trụ sở — hai pháp nhân khác nhau, gộp lại là sai |
| `normalizeName` | bỏ dấu, lowercase, gom khoảng trắng, bỏ ký tự đặc biệt | Chỉ dùng để **so** tên, không dùng để lưu |
| `normalizeText` | chỉ trim | Dùng cho `provinceCode` / `unitCode` |
| `calculateNameSimilarity` | Levenshtein → % | `(1 - distance/maxLength) * 100` |

**Ba đường ghi dữ liệu đều đã dùng bộ này:**

| Đường ghi | Class |
|---|---|
| Kafka ingest | `ProfileNormalizationService` (uỷ quyền toàn bộ cho `IdentityUtils`) |
| API admin tạo/sửa hồ sơ | `MasterProfileServiceImpl.create()` / `update()` |
| Pool candidate + scorer | `ProfileMatchingService`, `ProfileMatchScoreService`, `ProfileMergeDecisionService`, `ProfileMatchCandidateServiceImpl` |

> **Dữ liệu cũ:** hàng đã tồn tại trước thay đổi này vẫn ở dạng lệch. Chạy `src/main/resources/db/identity_normalization_migration.sql` **SAU** khi deploy code — chạy ngược thứ tự thì dữ liệu vừa chuẩn hoá sẽ bị code cũ ghi lệch lại. Sau đó gọi `POST /api/v1/admin/profile-match-candidates/detect/{masterProfileId}` cho các hồ sơ vừa trở nên trùng nhau (file migration có sẵn query tìm danh sách).

**Lưu ý về index:** `ingestion_indexes.sql` đã có `idx_pil_type_value` trên `(identity_type, identity_value)` nhưng là **partial index** `WHERE status = 1`. Postgres chỉ dùng được partial index khi predicate của query bao hàm predicate của index — mà `findByIdentityTypeAndIdentityValue()` sinh ra SQL **không** có điều kiện status (code lọc `status=1` ở tầng Java sau khi lấy về), nên index đó không dùng được cho đúng truy vấn nóng nhất. Migration tạo index đầy đủ với tên khác (`idx_pil_type_value_all`) — không dùng lại tên cũ, vì `CREATE INDEX IF NOT EXISTS` trùng tên sẽ là no-op âm thầm. Cùng vấn đề với `idx_pil_src_customer`.

---

## 3. Pipeline hồ sơ — 7 bước trong `ProfileIngestionServiceImpl.process()`

### B1. Lưu raw trước mọi thứ
`profile_source_records` với `mergeStatus=0` (PENDING), giữ nguyên `rawPayload`. Ghi **trước** khi xử lý nên mọi message đều có vết, kể cả khi crash ngay sau đó.

### B2. Normalize — `ProfileNormalizationService.normalize()`
Đọc payload dạng `Map<String,Object>` → `NormalizedProfileData` + `normalizedPayload` (lưu lại DB):

| Field | Xử lý |
|---|---|
| `fullName` | trim + gom nhiều space thành một |
| `phone` | `IdentityUtils.normalizePhone` → `0912345678` |
| `email` | `IdentityUtils.normalizeEmail` |
| `identityNo`, `taxCode` | `IdentityUtils.normalizeIdentityNo` |
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

Hai bộ lọc áp lên mọi nhánh:

- **Bỏ hồ sơ MERGED (3) / DELETED (5)** (`isUsable`). Hồ sơ đã merge là "bia mộ" — nó đã trỏ `mergedIntoProfileId` sang chỗ khác; nếu để lọt vào pool thì AUTO_MERGE sẽ ghi dữ liệu vào hồ sơ chết và dữ liệu đó không còn hiển thị ở đâu.
- **Suppression theo tần suất** (`MAX_PROFILES_PER_KEY = 20`). Một giá trị khóa khớp quá nhiều hồ sơ thì không còn tính phân biệt — thực tế là SĐT rác / hotline shipper / `0000000000`. Nhận hết sẽ đẩy mọi record vào nhánh CONFLICT với hàng nghìn candidate, nên bỏ qua khóa đó và ghi `log.warn` để đội dữ liệu xử lý.

> **Ngưỡng 20 là con số chọn theo phán đoán, chưa hiệu chỉnh theo phân bố dữ liệu thật.** Nên xem lại sau khi có số liệu production.

### B5. Quyết định — `ProfileMergeDecisionService.decide()`

Trả về `MergeDecisionResult(decision, target)`, **không** phải enum đơn thuần. Bắt buộc như vậy: khi có nhiều candidate, hồ sơ được chọn không chắc nằm ở index 0, nếu chỉ trả enum thì caller phải đoán bằng `candidates.get(0)` và sẽ **merge dữ liệu vào sai hồ sơ khách hàng**.

```
source system không hợp lệ                          → REJECT
không có khóa nào dùng được                         → REJECT
0 candidate                                         → CREATE_NEW_PROFILE

nhiều candidate → resolveSingleCandidate(): tách bằng khóa mạnh
     1. link nguồn (sourceSystem + sourceCustomerId, status=1)
     2. CCCD
     3. MST
     4. khóa typed (KHL / CRM / PostID / AppUser / Payment)
   → đúng MỘT candidate khớp  → nó là đích, chạy tiếp chain bên dưới
   → 0 hoặc ≥2 candidate khớp → CONFLICT

đã chốt 1 candidate → chain deterministic-first:
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

**Khóa được coi là "dùng được"** (`hasUsableIdentity`): `identityNo`, `phone`, `email`, `sourceCustomerId`, `taxCode`, `postId`, `khlCode`, `crmId`, `appUserId`, `paymentId`. Danh sách này phải khớp với bộ khóa mà B4 thực sự tra candidate — thiếu khóa nào ở đây thì record chỉ có khóa đó sẽ bị REJECT dù hệ thống hoàn toàn đủ khả năng khớp. `buildRejectReason()` ở `ProfileIngestionServiceImpl` dùng cùng danh sách, sửa một chỗ phải sửa cả hai.

**Nguyên tắc:** khóa mạnh (CCCD, MST, PostID, CRM ID, mã KHL) quyết định trước; điểm cộng dồn (tên gần đúng, ngày sinh, tỉnh, đơn vị) chỉ là **fallback** khi không có khóa mạnh nào so được. Nguyên tắc này áp dụng cả khi có nhiều candidate — đó là lý do `resolveSingleCandidate()` tồn tại.

**Vì sao nhiều candidate không còn mặc nhiên là CONFLICT:** nhiều hồ sơ dùng chung SĐT là chuyện bình thường (người thân, shipper). Nếu record đến khớp CCCD tuyệt đối với một trong số đó, thì đây không phải xung đột — đó là một ca khớp rõ ràng kèm vài hồ sơ nhiễu. Các candidate **không** được chọn vẫn không bị bỏ rơi: `detectAndCreateCandidatesForProfile()` async sẽ phát hiện chúng qua tín hiệu yếu và tạo match candidate để đối soát (mục 4).

**Ngưỡng 70 ở đây cố tình KHÔNG hạ xuống 35** như luồng admin. Nhánh `CREATE_MATCH_CANDIDATE` dùng `createProfileForReview()`, hàm này **không** publish `ProfileMergedEvent` nên không sync Unomi và không ghi change log. Hạ ngưỡng sẽ làm mọi match yếu (chỉ trùng SĐT 40đ, chỉ trùng email 35đ) vô hình với segment/campaign cho tới khi admin xử lý tay. Thay vào đó match yếu đi đường `createNewProfile` đầy đủ, rồi được detect async gắn cờ ở sàn 35.

### B6. Thực thi — `switch (decision)` → `ProfileMergeExecutorService`

Hồ sơ đích lấy từ `decisionResult.target()` cho 3 nhánh `AUTO_MERGE` / `NEED_REVIEW` / `CREATE_MATCH_CANDIDATE`. Nhánh `CONFLICT` dùng cả danh sách `candidates`.

| Decision | Làm gì | `mergeStatus` | Unomi | Detect candidate |
|---|---|---|---|---|
| `CREATE_NEW_PROFILE` | tạo `MasterProfile` (`MP_yyyyMMdd_XXXXXXXX`) + identity links (primary + enrichment) + attribute values (`isSelected=true`) + change logs | 1 MERGED | ✅ | ✅ |
| `AUTO_MERGE` | ghi attribute values (`isSelected=false`), rồi **rule engine từng field** (`ProfileMergeEngineService.shouldOverwrite`) quyết định ghi đè; mỗi lần ghi đè → 1 change log + đổi `isSelected` | 1 MERGED | ✅ | ✅ |
| `CREATE_MATCH_CANDIDATE` | `createProfileForReview()` tạo hồ sơ MỚI (không sync, không đánh MERGED) → `createCandidateBetweenProfiles()` tạo cặp PENDING | 4 NEED_REVIEW | ❌ | ❌ |
| `NEED_REVIEW` | **không** sửa master profile; ghi attribute values `isSelected=false` + `profile_merge_conflicts` cho phone/email/identityNo lệch (`fullName` cố tình loại — khác dấu/format không phải xung đột) | 4 NEED_REVIEW | ❌ | ❌ |
| `CONFLICT` | 1 dòng conflict `propertyName=PROFILE_MATCH` liệt kê id các candidate; **không chạm** hồ sơ nào | 2 CONFLICT | ❌ | ❌ |
| `REJECT` | chỉ ghi lý do vào `errorMessage` | 3 REJECTED | ❌ | ❌ |

Trong `autoMerge()`, bước "đảm bảo đã có identity link" hỏi đúng câu: **hồ sơ đích đã có link ACTIVE cho source này chưa**, thay vì "có ai đó đã link chưa". Link `status=3` (MERGED) sót lại từ lần merge trước không được tính là đã link; và khi `decide()` chọn được đích trong nhiều candidate, link cũ có thể đang trỏ sang candidate **khác** nên vẫn phải tạo link mới cho đích.

### B7. Exception
`catch` ngoài cùng đánh `mergeStatus=5` (ERROR) + `errorMessage` (cắt 2000 ký tự), **không rethrow** → consumer vẫn `acknowledge()`. Message **không retry**, chỉ còn vết trong source record (xem mục 8.5).

---

## 4. Xử lý async sau commit

Chỉ 2 nhánh `CREATE_NEW_PROFILE` và `AUTO_MERGE` publish `ProfileMergedEvent`.
`ProfileMergedEventListener` chạy `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`:

1. `unomiService.syncProfileToUnomi()` → ghi `profile_unomi_sync_logs` (status 1 SUCCESS / 2 FAILED), set `syncedToUnomiAt`.
2. `matchCandidateService.detectAndCreateCandidatesForProfile()` → sinh candidate cho hàng đợi đối soát của admin. Lỗi chỉ `log.warn`, **không** ảnh hưởng kết quả ingestion.

> Listener này là **điểm nối duy nhất** giữa auto-merge của ingestion và hàng đợi đối soát của admin. Nó cũng là nơi match yếu (<70 điểm, không đủ cho `CREATE_MATCH_CANDIDATE`) được gắn cờ — vì sàn của detect là 35. Lưu ý mặt yếu: lỗi ở bước 2 chỉ `log.warn`, nên nếu detect chết thì candidate mất âm thầm, không có cảnh báo.

---

## 5. Chấm điểm & deterministic-first

### 5.1 Bảng điểm cộng dồn — `ProfileMatchScoreService`

| Tín hiệu | Điểm |
|---|---|
| `identityNo` khớp | 50 |
| `taxCode` khớp | 50 |
| `phone` khớp | 40 |
| `email` khớp | 35 |
| `fullName` khớp tuyệt đối | 30 |
| tên tương đồng ≥90% / ≥85% / ≥75% | 20 / 15 / 5 |
| `dateOfBirth` khớp | 20 |
| `provinceCode` khớp | 10 |
| `unitCode` khớp | 5 |

Cap ở 100. `matchLevel`: ≥95 `VERY_HIGH`, ≥85 `HIGH`, ≥70 `MEDIUM`, còn lại `LOW`. `autoMergeRecommended = score ≥ 95 && !identityConflict`.

`identityConflict = true` khi hai bên đều có `identityNo` mà lệch, hoặc đều có `taxCode` mà lệch. Cờ này **ép** `autoMergeRecommended=false` bất kể điểm — nên một cặp lệch CCCD nhưng khớp mọi tín hiệu khác (điểm cộng dồn có thể tới 100) vẫn ra `NEED_REVIEW`. Lệch phone/email thì chỉ ghi một dòng reason, **không** set cờ này.

> **Mọi trọng số đều là bội số của 5**, nên mọi điểm cộng dồn cũng là bội số của 5. Đây là tính chất được dùng có chủ đích ở mục 5.2: điểm deterministic chọn giá trị **không** phải bội số của 5 để nhìn score là biết candidate đến từ nhánh nào. Nếu sau này thêm trọng số không phải bội số của 5 thì tính chất này mất, cần chọn lại các mốc deterministic.

### 5.2 Deterministic-first cho luồng admin — `ProfileMatchCandidateServiceImpl.resolveMatch()`

**Bối cảnh:** scorer là additive nên hai hồ sơ trùng CCCD (hoặc trùng MST) hoàn toàn chỉ được 50/100 — không đạt ngưỡng auto-merge và bị xếp ngang một cặp chỉ trùng vài tín hiệu yếu. Luồng ingestion đã có deterministic-first sẵn (B5), luồng admin thì trước đây gọi thẳng `calculate()`.

`resolveMatch(left, right)` luôn gọi `calculate()` trước để có đủ reasons, rồi:

| Điều kiện | Kết quả |
|---|---|
| CCCD hai bên đều có, **lệch** | trả nguyên kết quả probabilistic (`identityConflict=true` → `autoMergeRecommended` đã false) |
| CCCD hai bên đều có, **khớp** | score = **98**, `VERY_HIGH` |
| MST hai bên đều có, **lệch** | trả nguyên kết quả probabilistic |
| MST khớp + tên ≥75% (hoặc thiếu tên một bên) | score = **96**, `VERY_HIGH` |
| MST khớp + tên <75% | giữ score, **force** `autoMergeRecommended=false` |
| Khớp khóa typed (KHL / CRM / PostID / AppUser / Payment) | score = **97**, `VERY_HIGH` |
| Không so được khóa mạnh nào | probabilistic thuần, không đổi |

Thứ tự xét: CCCD → MST → khóa typed → probabilistic (mirror B5 của ingestion).

**Quyết định thiết kế:**
- **98 / 97 / 96 thay vì 100 / 95** — các mốc này **không** phải bội số của 5 nên không thể trùng với điểm cộng dồn (mục 5.1), nhìn score là biết candidate đến từ nhánh deterministic hay additive. `95` từng bị dùng cho khóa typed là sai: nó là bội số của 5 và additive chạm được thật (SĐT 40 + email 35 + tên sim≥90 20 = 95).
- **CCCD > MST** vì MST có thể bị dùng chung. Khóa typed nằm giữa: mạnh (do hệ thống nguồn cấp, duy nhất) nhưng không phải định danh pháp lý.
- **Giữ ĐỦ danh sách reason** (không rút còn 1 dòng) — màn đối chiếu cần thấy đủ bằng chứng, và `matchedKeys` ở màn nhóm lấy trực tiếp từ bảng `profile_match_reasons` nên rút bớt sẽ mất khoá khớp trên UI.
- **Nhánh xung đột không tự đặt điểm** — nếu trả score thấp cố định thì `createCandidate()` sẽ throw `SCORE_TOO_LOW` và `detectAndCreate` sẽ skip, làm **mất** candidate review.
- **`autoMergeRecommended = !identityConflict`** (không hard-code `true`) — thận trọng hơn ingestion một bậc ở đúng một case: **trùng CCCD nhưng lệch MST**, ingestion sẽ AUTO_MERGE còn admin thì không. Lý do: giữ invariant của scorer (`autoMerge ⇒ !conflict`) và **merge chưa có cơ chế unmerge**.
- **KHÔNG auto-merge ở luồng admin** — candidate luôn PENDING, admin bấm tay. `autoMergeRecommended` hiện chỉ được đọc ở `ProfileMergeDecisionService` (ingestion); entity `ProfileMatchCandidate` cũng không có cột lưu cờ này.
- **Sàn tạo candidate của admin = 35** (`MIN_CANDIDATE_SCORE`), thấp hơn ingestion. Để match yếu nhưng có thật — chỉ trùng SĐT (40đ), chỉ trùng email (35đ) — được gắn cờ tin cậy thấp thay vì âm thầm biến thành hồ sơ mới. Cố ý loại match **chỉ-tên** (30đ) vì tên tiếng Việt quá dễ trùng (`Nguyễn Văn A`), nhận vào sẽ làm ngập màn đối soát bằng gợi ý vô ích.
- **Pool detect phải chứa đủ khóa mạnh** — `detectAndCreateCandidatesForProfile()` nạp candidate theo `identityNo`, `taxCode`, `phone`, `email` **và** các link typed. Thiếu khóa nào trong pool thì nhánh deterministic tương ứng của `resolveMatch()` **không bao giờ** được kích hoạt, vì hai hồ sơ không được ghép cặp để so ngay từ đầu.

**Side-effect vận hành:** cặp trùng CCCD/MST/khóa typed từng bị admin IGNORED/REJECTED ở score ~50 sẽ có score mới 96–98 → diff ≥10 → **được tạo lại một lần** sau khi deploy. Cộng thêm việc hạ sàn xuống 35, hàng đợi PENDING sẽ tăng đáng kể ở lần chạy detect đầu tiên.

---

## 6. Luồng admin (`ProfileMatchCandidateController`)

Base path: `/api/v1/admin/profile-match-candidates`. Đọc từ `profile_match_candidates` mà pipeline trên đã sinh:

| Endpoint | Việc |
|---|---|
| `searchPendingGroups()` | màn "Đối soát định danh" — gom candidate PENDING theo hồ sơ gốc (native `UNION ALL` + `GROUP BY`, phân trang ở DB) |
| `getById()` / `search()` | màn đối chiếu 2 vế + danh sách reason (bằng chứng) |
| `createCandidate(l, r)` | admin tự ghép tay 2 hồ sơ — **cũng đi qua `resolveMatch()`** |
| `detect/{masterProfileId}` | chạy `detectAndCreateCandidatesForProfile()` thủ công |
| `ignore()` / `reject()` | đóng candidate; muốn tạo lại thì score mới phải cao hơn **≥10 điểm** |
| `merge()` | gộp thật (xem dưới) |

`merge()` theo thứ tự: `mergeProfileData` (chỉ fill field trống) → copy identity links + attribute values → `reassignReferencedData` (re-point `customer_events` / `profile_source_records` / `profile_merge_conflicts` sang target) → source thành `status=3 MERGED` + `mergedIntoProfileId` → expire các candidate PENDING còn trỏ tới source → sync **cả** target và source lên Unomi (source để Unomi biết hồ sơ đã chết, tránh đếm trùng ở segment).

---

## 7. Luồng event hành vi (`cdp.customer.events`)

Ngắn hơn nhiều, **không** qua matching/merge:

1. `saveEvent()` — tra `profile_source_records` **mới nhất** theo `(sourceSystem, sourceCustomerId)` → lấy `masterProfileId` → load `MasterProfile`. Bắt buộc kiểm `masterProfileId != null` trước khi `findById`: nhánh CONFLICT/REJECT của luồng hồ sơ để trường này null.
2. Match được → lưu `customer_events` kèm `masterProfileId` / `profileCode`, `syncStatus=0` PENDING.
   Không match → lưu với `masterProfileId=null`, `syncStatus=3` UNMATCHED và **dừng** (không push Unomi).
3. `syncToUnomi()` — build `UnomiEventRequest` (`profileId = profileCode`); riêng `createOrder` bơm thêm `transactionDate` từ `occurredAt`; timeout 5s → `syncStatus` 1 SUCCESS / 2 FAILED.

---

## 8. Gap / rủi ro còn tồn tại

### 8.1 `AUTO_MERGE` bỏ dữ liệu incoming mà không để lại vết
Nếu rule engine chặn ghi đè, giá trị incoming chỉ còn ở `profile_attribute_values` với `isSelected=false`, **không có change log** (chỉ log ra file). Muốn truy "vì sao giá trị này không vào master" phải đọc log ứng dụng.

### 8.2 Tier "gợi ý tin cậy thấp" chưa tách khỏi hàng đợi review thật
Candidate 35–69 điểm nằm cùng `STATUS_PENDING`, cùng `searchPendingGroups`, ngồi cạnh match CCCD 98 điểm. Chỉ phân biệt được qua `matchLevel=LOW`. Ngoài ra `hasLowConfidence` tính theo `minScore` của nhóm, nên một nhóm có 1 match CCCD chắc chắn + 1 gợi ý 40đ vẫn bị gắn cờ "tin cậy thấp" → gây hiểu nhầm.

### 8.3 Nhiều AUTO_MERGE hơn, mà không có unmerge
`resolveSingleCandidate()` (B5) chuyển một phần ca CONFLICT thành AUTO_MERGE. Đây là chiều cần soi kỹ nhất khi vận hành: tách sai = gộp sai dữ liệu khách hàng vĩnh viễn, vì `merge()` không có cơ chế hoàn tác. Đã có unit test phủ phần tách candidate (mục 9) nhưng dùng fake repository, **chưa** kiểm chứng trên DB thật.

### 8.4 `MAX_PROFILES_PER_KEY = 20` chưa hiệu chỉnh
Con số chọn theo phán đoán. Quá thấp thì bỏ sót cụm trùng lặp thật; quá cao thì sinh candidate hàng loạt. Cần xem phân bố `GROUP BY phone HAVING count(*) > 1` trên dữ liệu thật để chốt.

### 8.5 Message không retry, không DLQ
Cả 2 consumer đều `acknowledge()` trong `catch`. Recovery duy nhất là đọc `profile_source_records` (mergeStatus=5) rồi reprocess thủ công. Luồng `customer_events` thì không có cả vết đó.

### 8.6 `merge()` không có unmerge
Đây là lý do các quyết định ở mục 5.2 nghiêng về phía thận trọng.

### 8.7 Lỗi ở detect async chỉ log.warn
`ProfileMergedEventListener` bắt exception của `detectAndCreateCandidatesForProfile()` và chỉ ghi warn. Nếu detect chết thì candidate mất âm thầm, không có cảnh báo và không có cơ chế chạy lại tự động.

---

### Các gap đã sửa (giữ lại để tra khi đọc code cũ / git history)

| Gap cũ | Trạng thái |
|---|---|
| `findById(null)` làm mất event ở nhánh CONFLICT/REJECT | **Đã sửa** — kiểm null trước `findById`, ghi UNMATCHED đúng thiết kế. Trước đây Spring Data ném `InvalidDataAccessApiUsageException`, consumer bắt rồi vẫn `acknowledge()` → event mất hẳn |
| Hai bộ normalize song song (`+84…` vs `84→0`) | **Đã sửa** — hợp nhất về `IdentityUtils` (mục 2). Thực tế có **ba** đường ghi, không phải hai: API admin (`MasterProfileServiceImpl`) ghi thô không chuẩn hoá gì |
| `findByPhone/Email/IdentityNo/TaxCode` trả `Optional` | **Đã sửa** — trả `List`. Nặng hơn mô tả cũ: Spring Data không "lấy 1 trong nhiều" mà **ném `IncorrectResultSizeDataAccessException`** khi có ≥2 dòng, nên 2 hồ sơ trùng SĐT làm record bị đánh ERROR thay vì được khớp |
| `findBySourceSystemAndSourceCustomerId` trả `Optional` | **Đã sửa** — trả `List`. Bị trigger sau **mọi** lần admin merge: `copyIdentityLinks()` để lại link `status=3` trên hồ sơ nguồn và tạo link `status=1` trên đích = 2 dòng cùng khóa |
| `>1 candidate` → CONFLICT ngay, bỏ qua chuỗi khóa mạnh | **Đã sửa** — `resolveSingleCandidate()` (B5). Kèm đổi kiểu trả về thành `MergeDecisionResult(decision, target)` |
| Pool candidate không lọc hồ sơ MERGED/DELETED | **Đã sửa** — `isUsable()` (B4) |
| `hasUsableIdentity` thiếu taxCode + khóa typed | **Đã sửa** — record chỉ có MST hoặc chỉ có PostID không còn bị REJECT |
| Khóa typed chưa có trong luồng admin | **Đã sửa** — `resolveMatch()` bước 3, điểm 97, kèm nạp pool (mục 5.2) |
| Match yếu <70 âm thầm thành hồ sơ mới, không gắn cờ | **Đã sửa** — sàn detect của admin hạ về 35; ngưỡng ingestion giữ 70 có chủ đích (B5) |

---

## 9. Test

Không có Maven trên máy dev hiện tại, nhưng `spring-boot-starter-test` đã có trong `pom.xml` nên `mvn test` chạy được bình thường trên CI.

| File | Phủ gì |
|---|---|
| `IdentityUtilsTest` | Mọi dạng ghi SĐT về cùng dạng chuẩn; MST chi nhánh **không** bị gộp với trụ sở; SĐT rác → null; tên khác dấu khớp 100% |
| `ProfileNormalizationServiceTest` | Chốt luồng ingest ghi ra **đúng** dạng chuẩn mà pool dùng để truy vấn. Test ở tầng `IdentityUtils` không bắt được lỗi "service có bản copy riêng" — phải chốt ở tầng này |
| `ProfileMergeDecisionServiceTest` | Toàn bộ bảng quyết định B5: REJECT, khóa mạnh 1 candidate, xung đột CCCD/MST, và **nhiều candidate** — gồm ca "target trả về phải là hồ sơ được chọn, KHÔNG phải `candidates.get(0)`" |

Dùng fake repository viết tay thay Mockito cho `ProfileMergeDecisionServiceTest`: các assert phụ thuộc vào việc lọc theo status/type của link, fake tường minh dễ đọc hơn một chuỗi `when/thenReturn`. Các method `JpaRepository` không dùng tới đều ném `UnsupportedOperationException` — nếu service sau này gọi thêm gì, test báo ngay chứ không âm thầm trả rỗng.

**Chưa phủ:** đường async (`ProfileMergedEvent` → detect → sinh candidate), rule engine per-field, luồng admin `merge()`, và toàn bộ tương tác DB thật. Không có integration test.

---

## 10. File tham chiếu

**Đường vào**
- `kafka/consumer/ProfileEventConsumer.java`, `kafka/consumer/CustomerEventConsumer.java`
- `ingestion/controller/ProfileIngestionController.java`, `ingestion/producer/ProfileIngestionProducerImpl.java`

**Pipeline hồ sơ**
- `ingestion/service/ProfileIngestionServiceImpl.java` — orchestrator 7 bước
- `ingestion/service/ProfileNormalizationService.java` — B2
- `ingestion/service/ProfileMatchingService.java` — B4
- `ingestion/service/ProfileMergeDecisionService.java` — B5
- `ingestion/service/ProfileMergeExecutorService.java` — B6
- `ingestion/enums/MergeDecision.java`, `ingestion/dto/MergeDecisionResult.java`

**Matching / merge**
- `profile/service/match/ProfileMatchScoreService.java` — additive scoring
- `profile/service/match/ProfileMatchCandidateServiceImpl.java` — `resolveMatch()` + admin flow
- `profile/service/ProfileMergeEngineService.java` — rule engine per-field
- `common/utils/IdentityUtils.java` — **bộ chuẩn hoá duy nhất** + Levenshtein
- `profile/event/ProfileMergedEvent.java`, `profile/event/ProfileMergedEventListener.java`

**Ghi hồ sơ ngoài luồng ingest**
- `profile/service/MasterProfileServiceImpl.java` — API admin tạo/sửa, cũng phải chuẩn hoá

**Luồng hành vi**
- `customer_event/service/CustomerEventServiceImpl.java`

**Schema / migration**
- `resources/db/schema.sql`, `resources/db/profile_identity_schema.sql`, `resources/db/profile_match_schema.sql`, `resources/db/customer_event_schema.sql`
- `resources/db/ingestion_indexes.sql` — index hiệu năng; chú ý nhiều index ở đây là **partial** (`WHERE status = 1`), xem mục 2
- `resources/db/profile_identity_fields_migration.sql` — bổ sung `tax_code` / `customer_tier` / `customer_group`
- `resources/db/identity_normalization_migration.sql` — chuẩn hoá dữ liệu cũ, chạy **sau** khi deploy code

> **Không có công cụ migration.** `application.properties` đặt `spring.jpa.hibernate.ddl-auto=none` và `spring.sql.init.mode=never`, pom không có Flyway/Liquibase. Nghĩa là mọi file SQL trên phải chạy **thủ công, đúng thứ tự**, và không có bảng nào ghi lại migration đã chạy — phải tự theo dõi. Thứ tự: `schema.sql` → `profile_identity_schema.sql` → `profile_match_schema.sql` → `customer_event_schema.sql` → `ingestion_indexes.sql` → `profile_identity_fields_migration.sql` → `identity_normalization_migration.sql`.

---

## 11. Liên quan

- `docs/customer-profile-identity-changelog.md` — bổ sung field định danh + FP2b/FP2c/FP3 cho ingestion
- `docs/scoring-segment-feature.md` — scoring RFM/CLV/churn (khác hoàn toàn với match score ở tài liệu này)
