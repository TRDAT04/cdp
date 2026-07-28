# Quy tắc Cache (Redis)

Áp dụng khi thêm, sửa hoặc xóa cache trong service layer hoặc repository.

## Khi nào dùng cache

Dùng cache khi:
- data ít thay đổi nhưng đọc thường xuyên (reference data, config)
- query phức tạp hoặc join nhiều bảng
- external API call tốn thời gian

Không dùng cache khi:
- data thay đổi liên tục (gần real-time)
- payload lớn không cần thiết
- transaction cần consistency tuyệt đối

## Key naming

```
{entity}:{id}:{field}           → user:123:profile
{entity}:list:{filter}:{page}  → product:list:category=electronics:page=1
cache:{entity}:{id}             → cache:user:123
```

## Cache-aside pattern (đọc)

```
1. Kiểm tra cache
2. Nếu có → trả cache
3. Nếu không → đọc DB → ghi cache → trả kết quả
```

## Cache-aside pattern (ghi)

```
1. Ghi vào DB (trong transaction)
2. Xóa cache (KHÔNG ghi lại cache — tránh stale data)
```

## Invalidation

- Xóa cache khi entity được tạo / cập nhật / xóa
- Không ghi lại giá trị mới — bản ghi tiếp theo sẽ repopulate
- Dùng `@CacheEvict` hoặc manual evict tùy pattern hiện có trong repo
- Nếu dùng Redis Template, dùng `delete()` thay vì `set()` sau update

## TTL

- Reference data / config: TTL dài (1h–24h)
- Session-like data: TTL ngắn (15–60 phút)
- List query: TTL vừa (5–15 phút)
- Luôn đặt TTL cho mọi cache entry — không để vô hạn

## Distributed lock

Khi cần cache population đồng thời từ nhiều instance:
- Dùng `SETNX` / Redisson lock trước khi query DB
- Lock timeout ngắn (5–10 giây)
- Nếu không lấy được lock → đọc từ cache (dù stale) hoặc chờ

## Lưu ý

- KHÔNG cache object mang theo entity JPA (lazy-loading có thể gây exception sau khi evict session)
- KHÔNG cache toàn bộ query với filter động nếu combination quá nhiều (cache explosion)
- KHÔNG cache response chứa PII nếu không kiểm soát được TTL và access policy
- Đặt监控系统 cho cache hit/miss ratio

## Checklist review

Trước khi chốt:
- key convention có nhất quán không
- TTL đã đặt chưa
- eviction có được gọi đúng lúc không
- cache không chứa lazy entity hoặc PII
- có监控系统 cho hit/miss không