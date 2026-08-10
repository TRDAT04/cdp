# Hướng Dẫn Onboarding — VNP Example Service

Tài liệu này dành cho dev mới tham gia nhóm. Đọc từ đầu đến cuối trước khi viết bất kỳ dòng code nào.

---

## Mục lục

1. [Tổng quan dự án](#1-tổng-quan-dự-án)
2. [Thiết lập môi trường](#2-thiết-lập-môi-trường)
3. [Hiểu kiến trúc — điều quan trọng nhất](#3-hiểu-kiến-trúc--điều-quan-trọng-nhất)
4. [Thư viện vnpshared — base classes](#4-thư-viện-vnpshared--base-classes)
5. [Response format chuẩn](#5-response-format-chuẩn)
6. [Luồng đi của một request](#6-luồng-đi-của-một-request)
7. [Quy ước đặt tên — không được phá vỡ](#7-quy-ước-đặt-tên--không-được-phá-vỡ)
8. [Thêm module CRUD mới — từng bước](#8-thêm-module-crud-mới--từng-bước)
9. [BaseEntity — các field tự động](#9-baseentity--các-field-tự-động)
10. [Bảo mật và JWT](#10-bảo-mật-và-jwt)
11. [Các lỗi thường gặp của dev mới](#11-các-lỗi-thường-gặp-của-dev-mới)
12. [Checklist trước khi tạo PR](#12-checklist-trước-khi-tạo-pr)

---

## 1. Tổng quan dự án

| Thuộc tính | Giá trị |
|---|---|
| **Stack** | Java 21 · Spring Boot 4.0.2 · Maven |
| **Port** | 9000 |
| **Database** | PostgreSQL (DDL thủ công — Hibernate không tự tạo bảng) |
| **Cache** | Redis |
| **Auth** | Keycloak OAuth2 JWT |
| **Package gốc** | `vn.vnpost.example` |
| **Shared library** | `vn.vnpost:vnpshared:4.0.0` (repo nội bộ VNPost) |

**Mục đích:** Đây là microservice mẫu chuẩn của VNPost. Mọi service CRUD mới trong hệ thống đều được xây trên cùng bộ base classes từ `vnpshared`.

---

## 2. Thiết lập môi trường

### Yêu cầu

- JDK 21
- Maven 3.9+ (hoặc dùng Maven Wrapper `./mvnw` trong repo)
- Kết nối **VPN nội bộ VNPost** — bắt buộc để tải `vnpshared` từ `https://repos.vnpost.vn/repository/maven-public`
- Truy cập PostgreSQL, Redis, Keycloak UAT (hỏi team lead để lấy thông tin)

### Clone và build

```bash
git clone <repo-url>
cd vnp-example
./mvnw clean package -DskipTests
```

> Nếu build lỗi `Could not resolve vn.vnpost:vnpshared` → chưa bật VPN hoặc chưa có quyền truy cập Maven repo nội bộ.

### Chạy local

```bash
# Cách 1: dùng Maven Wrapper (đơn giản nhất)
./mvnw spring-boot:run

# Cách 2: build JAR rồi chạy
./mvnw clean package -DskipTests
java -jar target/vnp-example-0.0.1-SNAPSHOT.jar
```

### Env vars cần thiết

Tạo file `.env` local hoặc set trong IDE run config:

```
DATASOURCE_URL=jdbc:postgresql://<host>/a
DATASOURCE_USERNAME=postgres
DATASOURCE_PASSWORD=<hỏi team lead>
JWT_ISSUER_URI=https://uat-portal.vnpost.vn/kc/realms/vnpost
```

> **Không commit** thông tin credentials vào source code. File `application.properties` chỉ chứa giá trị fallback cho dev — credentials production phải qua env vars.

### Kiểm tra ứng dụng đã chạy

```
GET http://localhost:9000/actuator/health
→ { "status": "UP" }

GET http://localhost:9000/api-discovery
→ Danh sách tất cả endpoints
```

---

## 3. Hiểu kiến trúc — điều quan trọng nhất

```
Request
  │
  ▼
Controller (kế thừa BaseController)
  │  Chỉ nhận request, gọi service, trả response
  │  Không chứa if/else nghiệp vụ
  │
  ▼
Service Interface
  │
  ▼
ServiceImpl (kế thừa BaseServiceImpl)
  │  Toàn bộ logic nghiệp vụ ở đây
  │  Override validateInsert / validateUpdate nếu cần
  │
  ▼
Repository (kế thừa BaseRepos)
  │  Chỉ truy vấn database
  │
  ▼
PostgreSQL
```

**Ba quy tắc phân lớp không được vi phạm:**

1. **Controller không chứa if/else nghiệp vụ** — nếu thấy controller đang check điều kiện business, đó là code sai.
2. **Không gọi repository trực tiếp từ controller** — phải đi qua service.
3. **Không return JPA entity trực tiếp từ controller** — dùng `MethodResult` wrapper.

---

## 4. Thư viện vnpshared — base classes

Đây là trái tim của toàn bộ hệ thống. Mọi CRUD module đều kế thừa từ đây.

### BaseEntity

```java
// Mọi entity đều extend BaseEntity<Long>
public abstract class BaseEntity<T extends Serializable> {
    private T id;            // PK, auto-generate (IDENTITY)
    private UUID instanceId; // UUID tự động gán khi tạo
    private short status;    // Trạng thái: 1=active, 0=deleted (soft delete)
    private Date modified;   // Timestamp sửa lần cuối
    private String modifiedBy; // Username sửa lần cuối
}
```

> `instanceId` được tự động gán UUID ngẫu nhiên trong `@PrePersist`. Không cần set thủ công.

### BaseRepos

```java
// Có sẵn các method sau, không cần viết lại:
Optional<T> findByCode(String code);
Optional<T> findByInstanceId(UUID instanceId);
List<T> findByStatus(short status);
Page<T> findByCodeContaining(String code, PageRequest pageRequest);
```

> Entity **bắt buộc phải có field `code`** để `BaseRepos` hoạt động đúng (dùng cho search và findByCode).

### BaseServiceImpl — validation hooks

```java
// Override để thêm validate logic. Return false = reject, return true = cho phép.
public boolean validateInsert(T entity) throws Exception {
    // Mặc định: return true (không validate gì)
    // Override để chặn duplicate, check business rule, v.v.
}

public boolean validateUpdate(T entity) throws Exception {
    // Tương tự
}
```

### BaseController — endpoints có sẵn

Khi extend `BaseController<T, Long>`, bạn tự động có:

| Method | Path | `@CheckPermission` index | Mô tả |
|---|---|---|---|
| `POST` | `/v1/{resource}` | `1` — Create | Tạo mới |
| `PUT` | `/v1/{resource}/{id}` | `2` — Update | Cập nhật |
| `DELETE` | `/v1/{resource}/{id}` | `3` — Delete | Xóa mềm (set status=0) |
| `GET` | `/v1/{resource}/{id}` | `5` — Get by id | Lấy theo ID |
| `GET` | `/v1/{resource}/getByInstanceId/{uuid}` | *(không có)* | Lấy theo instanceId |
| `GET` | `/v1/{resource}/getsAll` | `6` — Gets all | Lấy tất cả (status=active) |
| `POST` | `/v1/{resource}/getsBySearch` | *(không có)* | Tìm kiếm có phân trang |

### @CheckPermission — bảng index đầy đủ

Annotation `@CheckPermission(index, title)` đánh dấu quyền cần có để gọi endpoint. Index được định nghĩa trong `EnumPermissionIndex`:

| Index | Enum | Title | Endpoint tương ứng |
|---|---|---|---|
| `1` | `INSERT` | Create | `POST /v1/{resource}` |
| `2` | `UPDATE` | Update | `PUT /v1/{resource}/{id}` |
| `3` | `DELETE` | Delete | `DELETE /v1/{resource}/{id}` |
| `4` | `DELETEMANY` | Delete many | *(chưa có trong BaseController)* |
| `5` | `GETBYID` | Get by id | `GET /v1/{resource}/{id}` |
| `6` | `GETSALL` | Gets all | `GET /v1/{resource}/getsAll` |

> Hai endpoint `getByInstanceId` và `getsBySearch` **chưa có** `@CheckPermission` — tức là không yêu cầu quyền, chỉ cần xác thực JWT.
> Nếu cần bảo vệ thêm, bạn có thể override method trong Controller con và thêm `@CheckPermission` với index phù hợp.

Khi thêm endpoint tùy chỉnh vào Controller con, bạn có thể dùng:

```java
@GetMapping("/export")
@CheckPermission(index = 6, title = "Gets all")  // tái dùng index phù hợp
public ResponseEntity<Object> export() { ... }
```

Constructor của BaseController nhận 2 tham số:

```java
super(service, sentryEnable);
// sentryEnable: false = không gửi lỗi lên Sentry (dùng false cho hiện tại)
```

---

## 5. Response format chuẩn

Mọi response API đều dùng `MethodResult`:

```json
// Thành công có data
{ "status": true, "success": true, "data": { ... }, "totalRecord": null }

// Thành công có danh sách + tổng số
{ "status": true, "success": true, "data": [...], "totalRecord": 42 }

// Lỗi
{ "status": false, "success": false, "message": "Mô tả lỗi", "data": null }
```

**Không tự tạo response format khác.** Dùng `MethodResult.success(data)`, `MethodResult.error(message)`.

---

## 6. Luồng đi của một request

Ví dụ: `POST /v1/salary-component`

```
1. SecurityConfig  → kiểm tra JWT (nếu endpoint yêu cầu auth)
2. BaseController.create()
   → lấy username từ JWT claim "preferred_username"
   → set entity.modifiedBy, entity.modified
   → gọi baseService.insert(entity)
3. BaseServiceImpl.insert()
   → gọi validateInsert(entity)     ← override trong ServiceImpl
   → nếu false: trả MethodResult.error(...)
   → nếu true: baseRepos.save(entity)
   → gọi writeSysLog() async        ← ghi audit log nếu syslog.save=true
   → trả MethodResult.success(entity)
4. Controller nhận MethodResult → trả ResponseEntity.ok(result)
```

---

## 7. Quy ước đặt tên — không được phá vỡ

| Thành phần | Quy ước | Ví dụ |
|---|---|---|
| Entity | PascalCase, danh từ | `ExampleTable` |
| Repository | `XxxRepos` | `ExampleTableRepos` |
| Service interface | `XxxService` | `ExampleTableService` |
| Service implementation | `XxxServiceImpl` | `ExampleTableServiceImpl` |
| Controller | `XxxController` | `ExampleTableController` |
| API path | `/v1/{kebab-case}` | `/v1/salary-component` |
| Table name | `snake_case` trong `@Table(name=...)` | `salary_component` |
| Column name | `snake_case` trong `@Column(name=...)` | `@Column(name = "code")` |
| JSON fields | `camelCase` | `salaryCode`, `modifiedBy` |

**Lưu ý quan trọng về column mapping:**

Hibernate dùng `PhysicalNamingStrategyStandardImpl` — tên cột trong `@Column(name=...)` phải khớp **chính xác** với tên cột trong database. Không có auto-convert camelCase → snake_case.

```java
// ĐÚNG
@Column(name = "salary_code")
private String salaryCode;

// SAI — Hibernate sẽ tìm cột "salaryCode" thay vì "salary_code"
@Column
private String salaryCode;
```

---

## 8. Thêm module CRUD mới — từng bước

Ví dụ: thêm module quản lý `Department` (phòng ban).

### Bước 1: Tạo bảng trong PostgreSQL

> `ddl-auto=none` — Hibernate **không tự tạo bảng**. Phải tạo thủ công.

```sql
CREATE TABLE department (
    id          BIGSERIAL PRIMARY KEY,
    "instanceId" UUID,
    status      SMALLINT NOT NULL DEFAULT 1,
    modified    TIMESTAMP,
    "modifiedBy" VARCHAR(255),
    code        VARCHAR(50),
    name        VARCHAR(255),
    description TEXT
);
```

> Chú ý các field từ `BaseEntity` dùng camelCase trong tên cột: `"instanceId"`, `"modifiedBy"` (có dấu ngoặc kép vì chứa chữ hoa).

### Bước 2: Tạo Entity

```java
package vn.vnpost.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.vnpost.shared.base.entity.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "department")
public class Department extends BaseEntity<Long> {

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;
}
```

**Quy tắc:**
- Bắt buộc có `@Getter @Setter` (Lombok)
- Bắt buộc `@Column(name=...)` tường minh cho mỗi field
- Bắt buộc có field `code` — `BaseRepos` dùng để search
- Không dùng `@Autowired` field — không có DI ở entity

### Bước 3: Tạo Repository

```java
package vn.vnpost.example.repository;

import org.springframework.stereotype.Repository;
import vn.vnpost.example.entity.Department;
import vn.vnpost.shared.base.BaseRepos;

@Repository
public interface DepartmentRepos extends BaseRepos<Department, Long> {
    // BaseRepos đã có: findByCode, findByInstanceId, findByStatus, findByCodeContaining
    // Chỉ thêm method khi cần query đặc thù
}
```

### Bước 4: Tạo Service Interface

```java
package vn.vnpost.example.service;

import vn.vnpost.example.entity.Department;
import vn.vnpost.shared.base.BaseService;

public interface DepartmentService extends BaseService<Department, Long> {
    // Chỉ khai báo method bổ sung nếu có
}
```

### Bước 5: Tạo ServiceImpl

```java
package vn.vnpost.example.service.impl;

import org.springframework.stereotype.Service;
import vn.vnpost.example.entity.Department;
import vn.vnpost.example.repository.DepartmentRepos;
import vn.vnpost.example.service.DepartmentService;
import vn.vnpost.shared.base.impl.BaseServiceImpl;

@Service
public class DepartmentServiceImpl extends BaseServiceImpl<Department, Long>
        implements DepartmentService {

    private final DepartmentRepos departmentRepos;

    public DepartmentServiceImpl(DepartmentRepos departmentRepos) {
        this.departmentRepos = departmentRepos;
    }

    @Override
    public boolean validateInsert(Department entity) throws Exception {
        var existing = departmentRepos.findByCode(entity.getCode());
        return existing.isEmpty();  // false = reject nếu code đã tồn tại
    }

    @Override
    public boolean validateUpdate(Department entity) throws Exception {
        var existing = departmentRepos.findByCode(entity.getCode());
        return existing.isEmpty() || existing.get().getId().equals(entity.getId());
    }
}
```

**Lưu ý:** Constructor injection — không dùng `@Autowired` trên field.

### Bước 6: Tạo Controller

```java
package vn.vnpost.example.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vnpost.example.entity.Department;
import vn.vnpost.example.service.DepartmentService;
import vn.vnpost.shared.base.BaseController;

@RestController
@RequestMapping("/v1/department")
public class DepartmentController extends BaseController<Department, Long> {

    public DepartmentController(DepartmentService departmentService) {
        super(departmentService, false);
    }
}
```

Sau bước này, bạn đã có đầy đủ 7 endpoints tự động:

```
POST   /v1/department
PUT    /v1/department/{id}
DELETE /v1/department/{id}
GET    /v1/department/{id}
GET    /v1/department/getByInstanceId/{uuid}
GET    /v1/department/getsAll
POST   /v1/department/getsBySearch
```

---

## 9. BaseEntity — các field tự động

Khi bạn tạo record mới, các field sau được tự động xử lý — **không cần set thủ công**:

| Field | Kiểu | Tự động bởi | Ghi chú |
|---|---|---|---|
| `id` | `Long` | DB (IDENTITY) | PK, auto-increment |
| `instanceId` | `UUID` | `@PrePersist` trong BaseEntity | UUID ngẫu nhiên |
| `modifiedBy` | `String` | `BaseController` — lấy từ JWT claim | Username từ Keycloak |
| `modified` | `Date` | `BaseController` và `BaseServiceImpl` | Timestamp hiện tại |
| `status` | `short` | Mặc định `1` (active) khi insert | `0` = đã xóa mềm |

---

## 10. Bảo mật và JWT

### Cấu hình hiện tại

`SecurityConfig.java` hiện tại cho phép tất cả request (`permitAll()`). Đây là cấu hình **dev/demo** — không dùng cho production.

### Endpoints không cần auth

- `/actuator/health/**`
- `/actuator/info`
- `/health-test/**`

### Lấy thông tin user từ JWT

Trong `BaseController`, username được lấy tự động từ JWT trước khi gọi service:

```java
String username = jwt.getClaimAsString("preferred_username");
if (username == null) username = jwt.getClaimAsString("username");
```

Keycloak thường dùng claim `preferred_username`. Field `username` là fallback.

### Khi cần bảo vệ endpoint tùy chỉnh

```java
@PreAuthorize("hasRole('ROLE_ADMIN')")
@GetMapping("/admin-only")
public ResponseEntity<Object> adminEndpoint() { ... }
```

---

## 11. Các lỗi thường gặp của dev mới

### "Column not found" khi chạy lần đầu

**Nguyên nhân:** `ddl-auto=none` — bảng chưa được tạo.
**Fix:** Tạo bảng thủ công trong PostgreSQL trước khi chạy app.

### `findByCode` trả `NullPointerException`

**Nguyên nhân:** Entity không có field `code` hoặc tên cột sai.
**Fix:** Đảm bảo entity có `@Column(name = "code")` và bảng có cột `code`.

### `validateInsert` không được gọi

**Nguyên nhân:** Quên `@Override` annotation hoặc sai signature.
**Fix:** Method phải khớp chính xác: `public boolean validateInsert(T entity) throws Exception`.

### `@Autowired` field bị `null` trong test

**Nguyên nhân:** Dùng field injection thay vì constructor injection.
**Fix:** Chuyển sang constructor injection:

```java
// SAI
@Autowired
private DepartmentRepos departmentRepos;

// ĐÚNG
private final DepartmentRepos departmentRepos;
public DepartmentServiceImpl(DepartmentRepos departmentRepos) {
    this.departmentRepos = departmentRepos;
}
```

### Response trả về JPA entity thô, có lazy-loading exception

**Nguyên nhân:** Controller return trực tiếp entity thay vì dùng `MethodResult`.
**Fix:** Dùng `MethodResult.success(entity)` — không return entity trực tiếp.

### Column mapping sai cho BaseEntity fields

**Nguyên nhân:** `BaseEntity` dùng tên cột có chữ hoa: `"instanceId"`, `"modifiedBy"`.
**Fix:** SQL tạo bảng phải dùng dấu ngoặc kép cho các tên này:

```sql
"instanceId" UUID,
"modifiedBy" VARCHAR(255)
```

### Không tải được vnpshared khi build

**Nguyên nhân:** Chưa kết nối VPN hoặc chưa có quyền truy cập Maven repo nội bộ.
**Fix:** Bật VPN VNPost. Nếu vẫn lỗi, liên hệ team lead để được cấp quyền.

---

## 12. Checklist trước khi tạo PR

### Code

- [ ] Entity extend `BaseEntity<Long>`, có `@Column(name=...)` cho mỗi field
- [ ] Entity có field `code` (required bởi `BaseRepos`)
- [ ] Repository extend `BaseRepos<T, Long>`, có `@Repository`
- [ ] Service interface extend `BaseService<T, Long>`
- [ ] ServiceImpl dùng **constructor injection**, không dùng `@Autowired` field
- [ ] Controller chỉ gọi service, không chứa logic nghiệp vụ
- [ ] Không return JPA entity trực tiếp — dùng `MethodResult`
- [ ] Không hardcode credentials, token, password trong source code

### Database

- [ ] Đã tạo bảng thủ công trong PostgreSQL
- [ ] Tên cột trong `@Column(name=...)` khớp chính xác với tên cột trong DB
- [ ] Cột `"instanceId"` và `"modifiedBy"` có dấu ngoặc kép trong SQL

### API

- [ ] Path theo chuẩn `/v1/{resource-kebab-case}`
- [ ] Không tạo API path trùng với endpoint có sẵn trong `BaseController`
- [ ] Endpoint mới cần bảo vệ đã có `@PreAuthorize`

### Build

- [ ] `./mvnw clean package` không có lỗi
- [ ] Đã test các endpoint cơ bản: create, update, delete, getById, getsAll

---

## Câu hỏi thường gặp

**Q: Tôi có cần viết SQL migration không?**
A: Hiện tại dự án chưa dùng Flyway. Bạn cần tạo bảng thủ công trong PostgreSQL và thông báo cho team. Nếu bảng quan trọng, viết SQL script vào `docs/` để team khác có thể tái tạo.

**Q: Làm sao thêm endpoint tùy chỉnh ngoài 7 endpoint mặc định?**
A: Thêm method vào Controller của bạn. Nhớ logic nghiệp vụ phải ở trong ServiceImpl, không ở Controller.

**Q: `BaseController` có `@CheckPermission` annotation — nó hoạt động như thế nào?**
A: Đây là annotation từ `vnpshared` để phân quyền. Mỗi index tương ứng một quyền (1=create, 2=update, 3=delete, 5=read, 6=list). Cơ chế này tích hợp với hệ thống phân quyền của VNPOST-COREAPI.

**Q: SysLog là gì? Tôi có cần làm gì không?**
A: `BaseServiceImpl` tự động ghi audit log (ai làm gì, lúc nào) qua `SysLogClientService` khi `syslog.save=true`. Dev không cần can thiệp — chỉ cần đảm bảo endpoint `syslog.host` đang hoạt động trong môi trường của bạn.

**Q: Virtual Threads là gì? Tôi có cần làm gì không?**
A: Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`) giúp handle concurrent requests hiệu quả hơn. Dev không cần làm gì thêm — chỉ tránh dùng `ThreadLocal` hoặc `synchronized` block trừ khi thực sự cần.