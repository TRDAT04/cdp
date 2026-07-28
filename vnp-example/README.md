# Claude Spring Boot Package

Bộ setup `.claude` chuẩn cho backend Spring Boot, dùng tiếng Việt.

## Cấu trúc

```
CLAUDE.md           ← entry point cho Claude, điền convention của project thật
.claude/
├── settings.json   ← cấu hình project + hooks
├── rules/          ← quy tắc bắt buộc (8 files)
├── commands/       ← lệnh tác vụ chuẩn (6 files)
├── agents/         ← agent chuyên trách (7 files)
└── hooks/          ← checklist trigger cho các thay đổi rủi ro (6 files)
```

## Rules (quy tắc bắt buộc)

| File | Áp dụng khi |
|---|---|
| `rules/coding-style.md` | mọi thay đổi Java |
| `rules/api-design.md` | thêm/sửa endpoint REST |
| `rules/security.md` | auth, endpoint mới, file upload |
| `rules/database.md` | entity, migration, repository, query |
| `rules/testing.md` | thêm/sửa test (Testcontainers) |
| `rules/review-checklist.md` | review PR hoặc diff |
| `rules/error-handling.md` | exception class, GlobalExceptionHandler |
| `rules/cache.md` | Redis cache |
| `rules/messaging.md` | Kafka / RabbitMQ |

## Commands (lệnh tác vụ)

| Lệnh | Mục đích |
|---|---|
| `/implement-endpoint` | thêm endpoint mới theo kiến trúc chuẩn |
| `/fix-bug` | sửa bug có regression test |
| `/review-pr` | review diff theo checklist |
| `/add-migration` | thêm Flyway migration an toàn |
| `/write-tests` | viết test đúng loại |
| `/refactor-safe` | refactor giữ nguyên hành vi |

## Agents (agent chuyên trách)

| Agent | Vai trò |
|---|---|
| `code-reviewer` | review code toàn diện |
| `security-reviewer` | rà bảo mật |
| `db-reviewer` | rà migration, query, index |
| `api-designer` | thiết kế/chuẩn hóa endpoint |
| `performance-reviewer` | rà hiệu năng |
| `integration-tester` | viết integration test |
| `test-writer` | viết/cập nhật unit test |

## Hooks (checklist tự động)

Hooks được kích hoạt tự động qua `settings.json`:

| Hook | Trigger |
|---|---|
| `pre-implement` | trước khi sửa Java |
| `endpoint-change-check` | trước khi thêm/sửa endpoint |
| `security-change-check` | trước khi thay đổi auth |
| `post-edit-java` | sau khi sửa Java |
| `migration-safety` | trước khi sửa SQL |
| `pre-commit-review` | trước khi commit |

## Cách copy vào repo mới

```bash
# 1. Copy toàn bộ
cp -r .claude/ /path/to/your-project/
cp CLAUDE.md /path/to/your-project/

# 2. Mở CLAUDE.md, điền phần "## Conventions đặc thù"
#    - stack thực (Java version, Spring Boot version)
#    - package base (com.mycompany.myproject)
#    - response wrapper format
#    - pagination format
#    - exception handling pattern
#    - logging format
#    - naming convention

# 3. Không cần chỉnh rules/commands/agents/hooks
```

## Cập nhật sau khi copy

Sau khi copy sang repo thật, kiểm tra và cập nhật:

- [ ] Java / Spring Boot version trong CLAUDE.md
- [ ] Build command (mvn / gradle)
- [ ] Package base name
- [ ] Response wrapper class name và format
- [ ] Pagination DTO class và format
- [ ] GlobalExceptionHandler class và exception mapping
- [ ] Package structure nếu khác mặc định (`controller/service/repository/domain/dto/config/exception/mapper`)
- [ ] MDC keys logging (traceId, userId...)
- [ ] Nếu dùng Redis: bổ sung cache key prefix và TTL policy
- [ ] Nếu dùng Kafka/RabbitMQ: bổ sung topic naming convention

## Quy tắc ưu tiên

1. Đọc rules liên quan **trước khi sửa code** — không nhảy thẳng vào sinh code
2. Nêu plan trước khi thay đổi lớn — file nào, rủi ro gì, test nào
3. Test đi kèm mọi thay đổi có ý nghĩa
4. Giữ diff nhỏ, tập trung, dễ review
5. Nhất quán với codebase hiện có hơn best practice chung chung