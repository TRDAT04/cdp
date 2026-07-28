# Quy tắc cơ sở dữ liệu

Áp dụng các quy tắc này cho thay đổi entity, repository, query và migration.

## Trước khi thay đổi phần liên quan DB
Hãy kiểm tra:
- entity hiện tại
- query repository hiện tại
- index hiện có
- lịch sử Flyway
- integration test liên quan

## Migration
- Dùng Flyway
- Không bao giờ sửa migration cũ đã áp dụng
- Chỉ thêm migration file mới
- Đặt tên migration có ý nghĩa
- Ưu tiên thay đổi bổ sung an toàn trước
- Đánh dấu rõ các thao tác phá hủy

## Thiết kế schema
- Tái sử dụng quy ước đặt tên hiện có
- Thể hiện rõ nullability
- Cẩn thận với default value
- Xem xét nhu cầu backfill dữ liệu
- Xem xét thứ tự deploy và khả năng tương thích

## An toàn query
- Kiểm tra nguy cơ N+1 query
- Kiểm tra thiếu index cho filter mới
- Kiểm tra hiệu quả phân trang
- Tránh load graph quá lớn nếu không cần
- Tránh native SQL nếu không thực sự cần

## Tối ưu query tìm kiếm cho bảng lớn

### Pattern tìm kiếm với LIKE/ILIKE

**Không tốt** (không dùng index):
```java
@Query("SELECT e FROM Entity e WHERE LOWER(e.field) LIKE LOWER(CONCAT('%', :keyword, '%'))")
```

**Tốt** (có thể dùng index):
```java
@Query(value = "SELECT e.* FROM table_name e WHERE " +
    "e.field ILIKE CONCAT(:keyword, '%')",
    nativeQuery = true)
```

**Lý do:**
- `LOWER()` function ngăn PostgreSQL dùng index
- Wildcard đầu `%keyword%` không thể dùng B-tree index
- `ILIKE 'keyword%'` với `text_pattern_ops` index có thể optimize

### Index cho tìm kiếm text

```sql
-- Index cho LIKE/ILIKE pattern 'keyword%'
CREATE INDEX idx_table_field_pattern
ON table_name (field text_pattern_ops);

-- Index cho tìm kiếm giữa chuỗi '%keyword%' (dùng trigram)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_table_field_trgm
ON table_name USING gin (field gin_trgm_ops);
```

**Khi nào dùng:**
- `text_pattern_ops`: tìm đầu chuỗi (`keyword%`) — nhanh, ít tốn storage
- `pg_trgm` (trigram): tìm giữa chuỗi (`%keyword%`) — chậm hơn, tốn storage hơn

### Query tìm kiếm đa cột với pagination

```java
@Query(value = "SELECT e.* FROM table_name e WHERE " +
    "(:keyword IS NULL OR :keyword = '' OR " +
    "e.column1 ILIKE CONCAT(:keyword, '%') OR " +
    "e.column2 ILIKE CONCAT(:keyword, '%'))",
    countQuery = "SELECT COUNT(*) FROM table_name e WHERE " +
        "(:keyword IS NULL OR :keyword = '' OR " +
        "e.column1 ILIKE CONCAT(:keyword, '%') OR " +
        "e.column2 ILIKE CONCAT(:keyword, '%'))",
    nativeQuery = true)
Page<Entity> search(@Param("keyword") String keyword, Pageable pageable);
```

**Lưu ý:**
- Phải có `countQuery` riêng cho pagination
- OR condition giữa nhiều cột → PostgreSQL có thể dùng Bitmap Index Scan
- Nếu có > 3 cột search, xem xét Full-Text Search

### Full-Text Search (cho tìm kiếm phức tạp)

```sql
-- Tạo tsvector column
ALTER TABLE table_name ADD COLUMN search_vector tsvector;

-- Trigger tự động update search_vector
CREATE TRIGGER tsvector_update BEFORE INSERT OR UPDATE
ON table_name FOR EACH ROW EXECUTE FUNCTION
tsvector_update_trigger(search_vector, 'pg_catalog.english', column1, column2);

-- Index cho Full-Text Search
CREATE INDEX idx_table_search_vector
ON table_name USING gin(search_vector);
```

```java
@Query(value = "SELECT e.* FROM table_name e WHERE " +
    "e.search_vector @@ to_tsquery('english', :query)",
    nativeQuery = true)
Page<Entity> fullTextSearch(@Param("query") String query, Pageable pageable);
```

**Khi nào dùng Full-Text Search:**
- Tìm kiếm > 3 cột
- Cần ranking/relevance score
- Cần stemming (tìm "running" match "run")
- Cần ignore stop words

## Quy tắc JPA/entity
- Giữ mapping entity đơn giản và nhất quán
- Cẩn thận với fetch strategy
- Tránh để lazy-loading gây tác dụng phụ sang service/controller
- Giữ đúng convention equals/hashCode mà repo đang dùng

## Checklist review
Trước khi chốt:
- migration có an toàn khi deploy không
- đã xem xét tương thích ngược chưa
- đã xem xét index chưa (đặc biệt cho search query)
- query có dùng được index không (kiểm tra với EXPLAIN ANALYZE)
- test repository/query đã cập nhật chưa
- integration coverage có đủ cho thay đổi rủi ro không
