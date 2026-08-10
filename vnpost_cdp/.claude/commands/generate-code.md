# Lệnh: generate-code
/generate-code CREATE TABLE system_environments (
id BIGSERIAL PRIMARY KEY,
system_id BIGINT NOT NULL,
environment VARCHAR(30) NOT NULL,
base_url VARCHAR(1000) NOT NULL,
gateway_url VARCHAR(1000),
timeout_ms INT DEFAULT 30000,
is_default BOOLEAN DEFAULT FALSE,
status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)
Mục tiêu:
Từ một đoạn SQL `CREATE TABLE`, sinh toàn bộ module CRUD Spring Boot theo đúng kiến trúc repo (Entity → Repository → Service → ServiceImpl → Controller).

## Input mong đợi
- Đoạn SQL `CREATE TABLE` (paste trực tiếp vào prompt kèm lệnh `/generate-code`)
- Ví dụ: `/generate-code  CREATE TABLE employee (...)`

## Cách làm việc

### Bước 1 — Phân tích SQL
Trước khi sinh code, đọc SQL và xác định:
- Tên bảng (snake_case) → tên Entity (PascalCase)
- Từng column → field Java, kiểu dữ liệu, nullable, `@Column(name=...)`
- Cột `id` (primary key) → kiểu ID (thường `Long`)
- Các cột đã có trong `BaseEntity` → **bỏ qua**, không map vào Entity con:
  `id`, `status`, `modified`, `modifiedBy`, `instanceId` (và các biến thể snake_case tương ứng)
- Constraints: NOT NULL → `@NotNull`/`@NotBlank`, UNIQUE → ghi chú để add index

### Bước 2 — Xác nhận kế hoạch trước khi sinh code
Trình bày ngắn để user xác nhận trước khi viết file:
- Tên Entity (PascalCase) và tên bảng (snake_case)
- Danh sách file sẽ tạo (với đường dẫn package đầy đủ)
- Danh sách field sẽ map: `column_name` → `javaField: JavaType`
- Các field bị bỏ qua (BaseEntity) và lý do
- Nếu có UNIQUE constraint: nêu rõ sẽ override validateInsert/validateUpdate
- **Hỏi user: "Có cần thêm API tìm kiếm và phân trang không?"**
- Có gì không chắc → hỏi trước, không tự giả định

### Bước 3 — Sinh code theo đúng thứ tự

1. **Entity** (`entity/XxxEntity.java`)
   - extends `BaseEntity<Long>`
   - `@Entity @Table(name = "ten_bang")`
   - Mỗi column dùng `@Column(name = "ten_column")` tường minh
   - Không thêm audit fields
   - Dùng Lombok: `@Getter @Setter` (không dùng `@Builder` trừ khi được yêu cầu)
   - Không override equals/hashCode

2. **Repository** (`repository/XxxRepos.java`)
   - extends `BaseRepos<XxxEntity, Long>`
   - Chỉ khai báo interface, không thêm method trừ khi SQL có UNIQUE constraint cần check duplicate

3. **Service interface** (`service/XxxService.java`)
   - extends `BaseService<XxxEntity, Long>`
   - Interface rỗng trừ khi có nghiệp vụ đặc biệt

4. **ServiceImpl** (`service/impl/XxxServiceImpl.java`)
   - extends `BaseServiceImpl<XxxEntity, Long>`
   - Override `validateInsert` / `validateUpdate` nếu có UNIQUE constraint
   - Constructor injection cho XxxRepos

5. **Controller** (`controller/XxxController.java`)
   - extends `BaseController<XxxEntity, Long>`
   - `@RequestMapping("/v1/ten-bang-kebab")`
   - Constructor injection cho XxxService

### Bước 4 — Sinh SQL index (nếu có UNIQUE/FK)
In ra script SQL tạo index tương ứng để chạy thủ công.

### Bước 5 — Sinh API tìm kiếm và phân trang (chỉ khi user yêu cầu)

**QUAN TRỌNG: Chỉ sinh phần này nếu user xác nhận "Có" ở Bước 2.**

Nếu user yêu cầu thêm API search, hỏi thêm:
- "Tìm kiếm theo cột nào?" (mặc định: tất cả cột text/varchar)
- "Cần tìm đầu chuỗi (`keyword%`) hay giữa chuỗi (`%keyword%`)?" (mặc định: đầu chuỗi)

Sau đó sinh theo pattern `getsBySearch` của `BaseController`:

1. **SearchModel** (`model/XxxSearchModel.java`)
   - extends `BaseSearchModel`
   - Thường chỉ dùng field `keyword` từ `BaseSearchModel` để tìm kiếm đa cột
   - Chỉ thêm field riêng nếu user yêu cầu filter cụ thể

2. **Repository search method** (`repository/XxxRepos.java`)
   - Thêm method `search()` với native query
   - Dùng `ILIKE` (PostgreSQL) cho case-insensitive search
   - Pattern: `keyword%` (không dùng `%keyword%`) để tận dụng index
   - Ví dụ:
     ```java
     @Query(value = "SELECT d.* FROM table_name d WHERE " +
         "(:keyword IS NULL OR :keyword = '' OR " +
         "d.column1 ILIKE CONCAT(:keyword, '%') OR " +
         "d.column2 ILIKE CONCAT(:keyword, '%'))",
         countQuery = "SELECT COUNT(*) FROM table_name d WHERE ...",
         nativeQuery = true)
     Page<XxxEntity> search(@Param("keyword") String keyword, Pageable pageable);
     ```

3. **ServiceImpl override** (`service/impl/XxxServiceImpl.java`)
   - Override `getsBySearch(BaseSearchModel searchModel)`
   - Parse `XxxSearchModel` từ `BaseSearchModel`
   - Xử lý pagination: `pageIndex`, `pageSize` (default 10)
   - Xử lý sorting: `orderCol`, `isDesc`
   - Gọi `repository.search()` và trả về `MethodResult.success(data, totalRecord)`

4. **Controller endpoint** (`controller/XxxController.java`)
   - Thêm method:
     ```java
     @PostMapping("/search")
     public ResponseEntity<MethodResult> search(@RequestBody XxxSearchModel searchModel) {
       MethodResult result = xxxService.getsBySearch(searchModel);
       return ResponseEntity.ok(result);
     }
     ```

5. **Database indexes** (Flyway migration)
   - Tạo file `src/main/resources/db/migration/Vx__create_xxx_indexes.sql`
   - Index với `text_pattern_ops` cho các cột search:
     ```sql
     CREATE INDEX IF NOT EXISTS idx_table_column1_pattern
     ON table_name (column1 text_pattern_ops);
     
     CREATE INDEX IF NOT EXISTS idx_table_column2_pattern
     ON table_name (column2 text_pattern_ops);
     ```

**Request example:**
```json
POST /v1/xxx/search
{
  "keyword": "search_term",
  "pageIndex": 0,
  "pageSize": 10,
  "orderCol": "columnName",
  "isDesc": false
}
```

**Response format:**
```json
{
  "status": true,
  "success": true,
  "message": null,
  "data": [...],
  "totalRecord": 100
}
```

### Bước 6 — Tóm tắt sau khi xong
1. Danh sách file đã tạo
2. Field mapping (SQL column → Java field)
3. SQL index cần tạo thủ công (hoặc Flyway migration đã tạo)
4. API endpoints có sẵn (CRUD + search nếu có)
5. Việc cần làm thêm: đăng ký ComponentScan nếu package mới, test cần viết

## Quy tắc bắt buộc
- Không thêm field audit (`createdAt`, `updatedAt`...) — đã có trong BaseEntity
- Không thêm logic trong controller
- `@Column(name=...)` phải khớp chính xác tên column trong SQL
- JSON field trả về phải là camelCase
- Không tạo DTO nếu không được yêu cầu — dùng Entity thẳng qua BaseController
- Không tạo custom exception nếu validation cơ bản đã đủ
- Đặt file đúng package theo cấu trúc repo

## Mapping kiểu dữ liệu SQL → Java

| SQL | Java |
|---|---|
| `BIGINT`, `BIGSERIAL` | `Long` |
| `INT`, `INTEGER`, `SERIAL` | `Integer` |
| `SMALLINT` | `Short` |
| `VARCHAR`, `TEXT`, `CHAR` | `String` |
| `BOOLEAN` | `Boolean` |
| `TIMESTAMP`, `TIMESTAMPTZ` | `LocalDateTime` |
| `DATE` | `LocalDate` |
| `TIME` | `LocalTime` |
| `NUMERIC`, `DECIMAL` | `BigDecimal` |
| `FLOAT`, `REAL` | `Double` |
| `UUID` | `java.util.UUID` |
| `JSONB`, `JSON` | `String` (serialize/deserialize thủ công) |

## Lưu ý tên cột đặc biệt từ BaseEntity

Các cột sau trong DB dùng dấu ngoặc kép vì có chữ hoa — SQL tạo bảng phải viết đúng:

```sql
"instanceId"  UUID,
"modifiedBy"  VARCHAR(255)
```

Nếu SQL đầu vào thiếu các cột này, nhắc user bổ sung trước khi chạy.