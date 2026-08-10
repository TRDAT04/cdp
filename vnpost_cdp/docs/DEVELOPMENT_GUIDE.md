# Hướng dẫn phát triển VNP Example Service

## 1. Tổng quan dự án

| Thuộc tính | Giá trị |
|---|---|
| **Tên dự án** | vnp-example |
| **Group ID** | `vn.vnpost` |
| **Artifact ID** | `vnpexample` |
| **Framework** | Spring Boot 4.0.2 |
| **Java version** | 21 (Virtual Threads enabled) |
| **Build tool** | Maven 3.9.5 (Maven Wrapper) |
| **Port** | 9000 |

**Mô tả:** Microservice mẫu chuẩn VNPost, cung cấp REST API CRUD với base classes từ thư viện chia sẻ `vnpshared`. Tích hợp sẵn OAuth2/JWT (Keycloak), PostgreSQL, Redis, và Kubernetes health probes.

---

## 2. Cấu trúc thư mục

```
vnp-example/
├── pom.xml                                     # Maven build descriptor
├── .mvn/wrapper/                               # Maven Wrapper
└── src/main/
    ├── java/vn/vnpost/example/
    │   ├── VnpExampleApplication.java          # Entry point (@SpringBootApplication)
    │   ├── common/
    │   │   ├── discovery/
    │   │   │   └── ApiDiscoveryController.java  # Tự động liệt kê API endpoints
    │   │   └── sercurity/
    │   │       └── SecurityConfig.java          # Cấu hình OAuth2 JWT
    │   ├── controller/
    │   │   ├── HealthTestController.java        # Mô phỏng health probe
    │   │   └── ExampleTableController.java   # CRUD salary component
    │   ├── entity/
    │   │   └── ExampleTable.java             # JPA entity
    │   ├── health/
    │   │   ├── MockDbHealthIndicator.java        # Mock DB health
    │   │   └── MockHealthIndicator.java          # Mock app health
    │   ├── repository/
    │   │   └── ExampleTableRepos.java         # Spring Data JPA repo
    │   └── service/
    │       ├── ExampleTableService.java       # Interface
    │       └── impl/
    │           └── ExampleTableServiceImpl.java # Implementation
    └── resources/
        └── application.properties               # Cấu hình ứng dụng
```

---

## 3. Kiến trúc hệ thống

```
┌──────────────────────────────────────────────────────────┐
│                    vnp-example (port 9000)                │
├──────────────────────────────────────────────────────────┤
│  REST Controller Layer                                   │
│  ├── ExampleTableController   /v1/salary-component    │
│  ├── HealthTestController        /health-test/**         │
│  └── ApiDiscoveryController      /api-discovery          │
├──────────────────────────────────────────────────────────┤
│  Service Layer                                           │
│  └── ExampleTableServiceImpl                          │
│       └── validateInsert / validateUpdate (hook pattern) │
├──────────────────────────────────────────────────────────┤
│  Repository Layer (Spring Data JPA)                      │
│  └── ExampleTableRepos → BaseRepos<T, ID>             │
├──────────────────────────────────────────────────────────┤
│  Infrastructure                                          │
│  ├── PostgreSQL  ─ JPA/Hibernate (ddl-auto=none)         │
│  ├── Redis       ─ Spring Data Redis                     │
│  └── Keycloak    ─ OAuth2 JWT Resource Server             │
├──────────────────────────────────────────────────────────┤
│  Shared Library: vnpshared 4.0.0                         │
│  ├── BaseEntity<ID>    ─ id, audit fields, soft-delete   │
│  ├── BaseRepos<T,ID>   ─ JpaRepository + shared queries  │
│  ├── BaseService<T,ID> ─ Service interface               │
│  ├── BaseServiceImpl   ─ CRUD + validation hooks         │
│  ├── BaseController    ─ REST CRUD endpoints             │
│  ├── DiscoveryController ─ API introspection             │
│  └── syslog            ─ System logging component        │
└──────────────────────────────────────────────────────────┘
```

---

## 4. Thư viện vnpshared — Base Classes

Thư viện `vn.vnpost:vnpshared:4.0.0` cung cấp bộ base classes chuẩn hóa CRUD:

| Base Class | Vai trò | Kế thừa bởi |
|---|---|---|
| `BaseEntity<ID>` | Entity gốc (id, audit, soft-delete) | `ExampleTable` |
| `BaseRepos<T, ID>` | Repository gốc (extends JpaRepository) | `ExampleTableRepos` |
| `BaseService<T, ID>` | Service interface gốc | `ExampleTableService` |
| `BaseServiceImpl<T, ID>` | Service implementation gốc | `ExampleTableServiceImpl` |
| `BaseController<T, ID>` | Controller CRUD gốc | `ExampleTableController` |

### BaseEntity — fields thực tế

```java
public abstract class BaseEntity<T extends Serializable> {
    private T id;              // PK — auto-generate (IDENTITY)
    private UUID instanceId;   // UUID ngẫu nhiên, gán tự động trong @PrePersist
    private short status;      // 1 = active (EnumStatus.USED), 0 = xóa mềm (EnumStatus.DELETED)
    private Date modified;     // Timestamp lần sửa cuối — set bởi BaseController / BaseServiceImpl
    private String modifiedBy; // Username lần sửa cuối — lấy từ JWT claim "preferred_username"
}
```

Tên cột trong database cho các field của `BaseEntity`:

| Java field | Tên cột DB | Ghi chú |
|---|---|---|
| `id` | `id` | |
| `instanceId` | `"instanceId"` | Có dấu ngoặc kép — chữ hoa trong tên cột |
| `status` | `status` | |
| `modified` | `modified` | |
| `modifiedBy` | `"modifiedBy"` | Có dấu ngoặc kép — chữ hoa trong tên cột |

> SQL tạo bảng phải dùng dấu ngoặc kép: `"instanceId" UUID`, `"modifiedBy" VARCHAR(255)`.

### EnumStatus — giá trị status

| Enum | Giá trị | Ý nghĩa |
|---|---|---|
| `EnumStatus.USED` | `1` | Active — record đang dùng |
| `EnumStatus.DELETED` | `0` | Đã xóa mềm — không hiển thị |

`getsAll()` chỉ trả về record có `status = 1`. Khi `delete()` được gọi, service set `status = 0` và save — không xóa khỏi DB.

### BaseRepos — methods có sẵn

```java
Optional<T> findByCode(String code);                              // tìm theo code (exact match)
Optional<T> findByInstanceId(UUID instanceId);                    // tìm theo UUID
List<T> findByStatus(short status);                               // lấy theo trạng thái
Page<T> findByCodeContaining(String code, PageRequest pageRequest); // search có phân trang
```

> Entity **bắt buộc phải có field `code`** — `BaseRepos` và `BaseServiceImpl.getsBySearch()` dùng field này.

### Validation Hooks

`BaseServiceImpl` cung cấp các hook để validate trước khi INSERT/UPDATE:

```java
// Override trong ServiceImpl — mặc định return true (bỏ qua validate)
@Override
public boolean validateInsert(T entity) throws Exception {
    // return false → BaseServiceImpl trả MethodResult.error(...)
    // return true  → tiếp tục save vào DB
}

@Override
public boolean validateUpdate(T entity) throws Exception { ... }

@Override
public boolean validateDelete(PK id) throws Exception { ... }  // hiện chưa được gọi trong BaseServiceImpl
```

### BaseController — @CheckPermission index

Mỗi endpoint trong `BaseController` được đánh dấu `@CheckPermission` với index tương ứng quyền trong hệ thống phân quyền:

| Index | `EnumPermissionIndex` | HTTP Method | Path | Mô tả |
|---|---|---|---|---|
| `1` | `INSERT` | `POST` | `/v1/{resource}` | Tạo mới |
| `2` | `UPDATE` | `PUT` | `/v1/{resource}/{id}` | Cập nhật |
| `3` | `DELETE` | `DELETE` | `/v1/{resource}/{id}` | Xóa mềm |
| `4` | `DELETEMANY` | — | *(chưa có endpoint)* | Xóa nhiều |
| `5` | `GETBYID` | `GET` | `/v1/{resource}/{id}` | Lấy theo ID |
| `6` | `GETSALL` | `GET` | `/v1/{resource}/getsAll` | Lấy tất cả |

Hai endpoint `GET /getByInstanceId/{uuid}` và `POST /getsBySearch` **không có** `@CheckPermission`.

Khi thêm endpoint tùy chỉnh, dùng index phù hợp với hành động:

```java
@GetMapping("/export")
@CheckPermission(index = 6, title = "Gets all")
public ResponseEntity<Object> export() { ... }
```

### MethodResult — response wrapper chuẩn

Tất cả endpoint đều trả về `MethodResult`. Không tự tạo response format khác.

```java
MethodResult.success(data)                  // { status: true, success: true, data: {...} }
MethodResult.success(data, totalRecord)     // thêm totalRecord cho danh sách có phân trang
MethodResult.success()                      // không có data (dùng cho delete)
MethodResult.error("message")               // { status: false, success: false, message: "..." }
MethodResult.exception("message")           // tương đương error — dùng trong catch block
```

### SysLog — audit log tự động

`BaseServiceImpl` ghi audit log sau mỗi INSERT / UPDATE / DELETE nếu `syslog.save=true`:

```properties
# application.properties
syslog.save=true
syslog.host=http://localhost:9001  # endpoint của service cksyslog
```

Dev không cần can thiệp — log được ghi async và không ảnh hưởng đến response.

---

## 5. Quy ước đặt tên

| Thành phần | Quy ước | Ví dụ |
|---|---|---|
| **Package gốc** | `vn.vnpost.example` | — |
| **Entity** | Danh từ, PascalCase | `ExampleTable` |
| **Repository** | `XxxRepos` | `ExampleTableRepos` |
| **Service** | `XxxService` (interface) | `ExampleTableService` |
| **Service Impl** | `XxxServiceImpl` | `ExampleTableServiceImpl` |
| **Controller** | `XxxController` | `ExampleTableController` |
| **API path** | `/v1/{resource-kebab-case}` | `/v1/salary-component` |
| **Table name** | `snake_case` | `salary_component` |

---

## 6. Hướng dẫn thêm module mới (CRUD)

### Bước 1: Tạo Entity

```java
package vn.vnpost.example.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.vnpost.shared.base.BaseEntity;

@Entity
@Table(name = "ten_bang")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class TenEntity extends BaseEntity<Long> {

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "name")
    private String name;

    // Thêm các field khác
}
```

### Bước 2: Tạo Repository

```java
package vn.vnpost.example.repository;

import org.springframework.stereotype.Repository;
import vn.vnpost.example.entity.TenEntity;
import vn.vnpost.shared.base.BaseRepos;

import java.util.Optional;

@Repository
public interface TenEntityRepos extends BaseRepos<TenEntity, Long> {
    Optional<TenEntity> findByCode(String code);
}
```

### Bước 3: Tạo Service Interface

```java
package vn.vnpost.example.service;

import vn.vnpost.example.entity.TenEntity;
import vn.vnpost.shared.base.BaseService;

public interface TenEntityService extends BaseService<TenEntity, Long> {
}
```

### Bước 4: Tạo Service Implementation

```java
package vn.vnpost.example.service.impl;

import org.springframework.stereotype.Service;
import vn.vnpost.example.entity.TenEntity;
import vn.vnpost.example.repository.TenEntityRepos;
import vn.vnpost.example.service.TenEntityService;
import vn.vnpost.shared.base.BaseServiceImpl;

@Service
public class TenEntityServiceImpl
        extends BaseServiceImpl<TenEntity, Long>
        implements TenEntityService {

    private final TenEntityRepos tenEntityRepos;

    public TenEntityServiceImpl(TenEntityRepos tenEntityRepos) {
        super(tenEntityRepos);
        this.tenEntityRepos = tenEntityRepos;
    }

    @Override
    public boolean validateInsert(TenEntity entity) {
        var existing = tenEntityRepos.findByCode(entity.getCode());
        return existing.isEmpty(); // false = reject (duplicate code)
    }

    @Override
    public boolean validateUpdate(TenEntity entity) {
        var existing = tenEntityRepos.findByCode(entity.getCode());
        return existing.isEmpty() || existing.get().getId().equals(entity.getId());
    }
}
```

### Bước 5: Tạo Controller

```java
package vn.vnpost.example.controller;

import org.springframework.web.bind.annotation.*;
import vn.vnpost.example.entity.TenEntity;
import vn.vnpost.example.service.TenEntityService;
import vn.vnpost.shared.base.BaseController;

@RestController
@RequestMapping("/v1/ten-entity")
public class TenEntityController extends BaseController<TenEntity, Long> {

    public TenEntityController(TenEntityService tenEntityService) {
        super(tenEntityService, false);
        // false = không cho phép truy cập không xác thực (cần JWT)
        // true  = cho phép truy cập không cần xác thực
    }
}
```

### Bước 6: Tạo bảng trong PostgreSQL

```sql
CREATE TABLE ten_bang (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50),
    name        VARCHAR(255),
    -- Các field từ BaseEntity (kiểm tra schema thực tế)
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    deleted     BOOLEAN DEFAULT FALSE
);
```

> **Lưu ý:** `ddl-auto=none`, bảng phải tạo thủ công hoặc qua migration script.

---

## 7. API Endpoints

### Business API (kế thừa từ BaseController)

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/v1/salary-component` | Danh sách (có phân trang) |
| `GET` | `/v1/salary-component/{id}` | Chi tiết theo ID |
| `POST` | `/v1/salary-component` | Tạo mới |
| `PUT` | `/v1/salary-component/{id}` | Cập nhật |
| `DELETE` | `/v1/salary-component/{id}` | Xóa |

### Actuator (Health Probes)

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/actuator/health` | Tổng hợp health |
| `GET` | `/actuator/health/readiness` | Readiness probe (db, mock, mockDb) |
| `GET` | `/actuator/health/liveness` | Liveness probe (ping, mock, mockDb) |

### Health Test (Mô phỏng)

| Method | Path | Mô tả |
|---|---|---|
| `POST` | `/health-test/fail` | Đặt app health → DOWN |
| `POST` | `/health-test/ok` | Đặt app health → UP |
| `POST` | `/health-test/db-down` | Đặt DB health → DOWN |
| `POST` | `/health-test/db-up` | Đặt DB health → UP |

### Utility

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api-discovery` | Liệt kê tất cả endpoints đã đăng ký |

---

## 8. Cấu hình môi trường

Ứng dụng sử dụng environment variables với giá trị mặc định fallback:

| Biến môi trường | Mặc định | Mô tả |
|---|---|---|
| `DATASOURCE_URL` | `jdbc:postgresql://172.20.0.70/a` | JDBC URL PostgreSQL |
| `DATASOURCE_USERNAME` | `postgres` | DB username |
| `DATASOURCE_PASSWORD` | *(hardcoded)* | DB password |
| `JWT_ISSUER_URI` | `https://uat-portal.vnpost.vn/kc/realms/vnpost` | Keycloak issuer |
| `SHOW_SQL` | `true` | Hiển thị SQL trong log |
| `HIKARI_MAX_POOL_SIZE` | `10` | Connection pool tối đa |
| `HIKARI_MIN_IDLE` | `1` | Connection pool tối thiểu |
| `HIKARI_CONECTION_TIMEOUT` | `3000` | Connection timeout (ms) |

---

## 9. Chạy ứng dụng

### Chạy local

```bash
# Sử dụng Maven Wrapper
./mvnw spring-boot:run

# Hoặc build JAR rồi chạy
./mvnw clean package -DskipTests
java -jar target/vnp-example-0.0.1-SNAPSHOT.jar
```

### Chạy với biến môi trường tùy chỉnh

```bash
DATASOURCE_URL=jdbc:postgresql://localhost:5432/mydb \
DATASOURCE_USERNAME=myuser \
DATASOURCE_PASSWORD=mypass \
JWT_ISSUER_URI=https://my-keycloak/realms/myrealm \
java -jar target/vnp-example-0.0.1-SNAPSHOT.jar
```

---

## 10. Bảo mật (Security)

### Cấu hình hiện tại

- **Phương thức:** OAuth2 JWT Resource Server (Keycloak)
- **CSRF:** Disabled (phù hợp cho stateless REST API)
- **Endpoints công khai:** `/actuator/health/**`, `/actuator/info`, `/health-test/**`
- **Các endpoint khác:** Hiện tại `permitAll()` — **chỉ dành cho môi trường dev/demo**

### Cấu hình cho Production

Thay đổi trong `SecurityConfig.java`:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        "/actuator/health", "/actuator/health/**", "/actuator/info"
    ).permitAll()
    // Xóa /health-test/** khỏi permitAll
    .anyRequest().authenticated()  // ← Thay permitAll() bằng authenticated()
)
```

---

## 11. Checklist khi phát triển tính năng mới

- [ ] Tạo bảng SQL trước (ddl-auto=none)
- [ ] Tạo Entity kế thừa `BaseEntity<Long>`
- [ ] Tạo Repository kế thừa `BaseRepos<T, Long>`
- [ ] Tạo Service interface kế thừa `BaseService<T, Long>`
- [ ] Tạo ServiceImpl kế thừa `BaseServiceImpl<T, Long>`
- [ ] Override `validateInsert` / `validateUpdate` nếu cần validate business logic
- [ ] Tạo Controller kế thừa `BaseController<T, Long>`
- [ ] Đặt API path theo chuẩn: `/v1/{resource-kebab-case}`
- [ ] Sử dụng constructor injection (không dùng `@Autowired` trên field)
- [ ] Không hardcode credentials trong source code
- [ ] Viết unit test cho validation logic
- [ ] Build và test: `./mvnw clean package`

---

## 12. Lưu ý quan trọng

### Thư viện vnpshared

- Maven repo nội bộ: `https://repos.vnpost.vn/repository/maven-public`
- Phải có quyền truy cập mạng nội bộ VNPost để tải dependency
- `BaseEntity` cung cấp sẵn các field audit (created_at, updated_at, deleted...)

### ComponentScan

`VnpExampleApplication` scan thêm package `vn.vnpost.shared.syslog`:

```java
@ComponentScan(basePackages = { "vn.vnpost.shared.syslog", "vn.vnpost.example" })
```

Khi thêm module từ vnpshared, có thể cần bổ sung package vào danh sách scan.

### Virtual Threads (Java 21)

Ứng dụng bật Virtual Threads (`spring.threads.virtual.enabled=true`), giúp xử lý concurrent requests hiệu quả hơn mà không cần tuning thread pool thủ công.

### Physical Naming Strategy

Hibernate dùng `PhysicalNamingStrategyStandardImpl` — tên cột trong `@Column(name=...)` phải khớp chính xác với tên cột trong database (không tự động chuyển camelCase → snake_case).
