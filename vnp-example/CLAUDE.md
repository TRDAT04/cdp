# CLAUDE.md — VNP Example Service

Hướng dẫn hành vi để giảm lỗi thường gặp khi làm việc với codebase này. Đọc **trước khi viết code**.

**Tradeoff:** Những hướng dẫn này ưu tiên sự an toàn hơn tốc độ. Với tác vụ đơn giản, dùng phán đoán hợp lý.

---

## 1. Nghĩ Trước Khi Code

**Không giả định. Không che giấu sự mơ hồ. Hỏi khi cần.**

- Nếu có nhiều cách hiểu, trình bày rõ — không tự chọn ngầm.
- Nếu có cách đơn giản hơn, nói ra. Phản biện khi cần thiết.
- Nếu thiếu ngữ cảnh: suy ra từ `ExampleTable` module mẫu → base classes → cấu hình chung → naming conventions.
- Ưu tiên nhất quán với codebase hiện có hơn best practice chung chung.

---

## 2. Thay Đổi Tối Thiểu

**Chỉ chạm vào những gì cần thiết. Không dọn dẹp code xung quanh.**

- Không thêm tính năng ngoài yêu cầu.
- Không tạo abstraction cho code chỉ dùng một lần.
- Không refactor khi chỉ sửa bug nhỏ.
- Giữ đúng style hiện có, dù bạn có thể làm khác.
- Nếu thay đổi tạo ra orphan (import/biến/method thừa), xóa chúng — nhưng chỉ những gì **bạn** tạo ra.

---

## 3. Kiến Trúc — Không Phá Vỡ Phân Lớp

**Stack**: Java 21 · Spring Boot 4.0.2 · Maven · PostgreSQL · Redis · Keycloak (OAuth2 JWT)  
**Port**: 9000 · **Package**: `vn.vnpost.example` · **DDL**: `none` (tạo bảng thủ công)

Mọi module CRUD kế thừa từ `vnpshared`:

```
BaseController<T, ID>  →  REST (GET/POST/PUT/DELETE)
BaseServiceImpl<T, ID> →  CRUD + validateInsert/validateUpdate hooks
BaseRepos<T, ID>       →  JpaRepository
BaseEntity<ID>         →  id, audit fields, soft-delete
```

**Quy tắc phân lớp:**
- Controller: không chứa if/else nghiệp vụ, không gọi repository trực tiếp
- Service: chứa toàn bộ logic nghiệp vụ
- Không return JPA entity từ controller — dùng DTO hoặc response wrapper

---

## 4. Conventions — Nhất Quán Tuyệt Đối

| Thành phần | Quy ước | Ví dụ |
|---|---|---|
| Entity | PascalCase danh từ | `ExampleTable` |
| Repository | `XxxRepos` | `ExampleTableRepos` |
| Service | `XxxService` / `XxxServiceImpl` | — |
| Controller | `XxxController` | — |
| API path | `/v1/{kebab-case}` | `/v1/salary-component` |
| Table | `snake_case` | `salary_component` |
| Column | `@Column(name=...)` tường minh | Không để JPA tự chuyển |
| JSON fields | `camelCase` | Không mix với `snake_case` |

**Dependency injection**: constructor injection — không dùng `@Autowired` field.

---

## 5. Bảo Mật & Secrets

- Không hardcode credentials, token, API key trong source code — dùng env vars
- Không log password/token/secret/PII
- Endpoint mới phải có `@PreAuthorize` nếu cần bảo vệ
- Không expose stack trace hoặc SQL error detail ra API response

---

## 6. Quy Tắc Chi Tiết

Đọc rules liên quan **trước khi sửa code**:

| Rule | Đọc khi |
|---|---|
| `.claude/rules/coding-style.md` | mọi thay đổi Java |
| `.claude/rules/api-design.md` | thêm/sửa endpoint REST |
| `.claude/rules/security.md` | auth, endpoint mới, file upload |
| `.claude/rules/database.md` | entity, repository, query |
| `.claude/rules/testing.md` | thêm/sửa test |
| `.claude/rules/error-handling.md` | xử lý lỗi, exception |
| `.claude/rules/cache.md` | Redis cache |
| `.claude/rules/messaging.md` | Kafka/RabbitMQ |
| `.claude/rules/review-checklist.md` | review PR hoặc diff |

---

## 7. Thêm Module CRUD Mới

1. Tạo bảng SQL thủ công trong PostgreSQL
2. Entity extends `BaseEntity<Long>` → `entity/`
3. Repository extends `BaseRepos<T, Long>` → `repository/`
4. Service interface extends `BaseService<T, Long>` → `service/`
5. ServiceImpl extends `BaseServiceImpl<T, Long>` → `service/impl/`
6. Controller extends `BaseController<T, Long>` → `controller/`

Chi tiết: [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md)

---

## 8. Endpoints Hiện Có

| Method | Path | Mô tả |
|---|---|---|
| CRUD | `/v1/salary-component` | Salary component |
| GET | `/actuator/health{/readiness,/liveness}` | Kubernetes probes |
| POST | `/health-test/fail\|ok\|db-down\|db-up` | Mô phỏng health |
| GET | `/api-discovery` | Liệt kê tất cả endpoints |

---

## 9. Build & Run

```bash
./mvnw spring-boot:run                          # Dev
./mvnw clean package -DskipTests               # Build JAR
java -jar target/vnp-example-0.0.1-SNAPSHOT.jar  # Run JAR
```

**Env vars quan trọng:**

| Biến | Mặc định |
|---|---|
| `DATASOURCE_URL` | `jdbc:postgresql://172.20.0.70/a` |
| `DATASOURCE_USERNAME` | `postgres` |
| `DATASOURCE_PASSWORD` | *(required)* |
| `JWT_ISSUER_URI` | `https://uat-portal.vnpost.vn/kc/realms/vnpost` |

Maven repo nội bộ: `https://repos.vnpost.vn/repository/maven-public` (cần VPN)

---

**Những guidelines này hiệu quả nếu:** diff tập trung, ít rewrite vì overcomplicate, câu hỏi làm rõ xuất hiện trước khi implement — không phải sau khi mắc lỗi.
