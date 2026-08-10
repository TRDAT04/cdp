# Hooks cho Spring Boot backend

## Vai trò thực

Hooks trong bộ này là **checklist trigger** — không phải rules độc lập.
Mục đích: đảm bảo các bước kiểm tra quan trọng diễn ra trước khi thay đổi được coi là hoàn tất, thay vì phụ thuộc vào model nhớ.

Hooks không thay thế rules — chúng kích hoạt rules đúng thời điểm.

## Mapping hook → trigger event

| Hook | Trigger khi | Làm gì |
|---|---|---|
| `pre-implement.md` | Trước khi sửa file Java | Checklist khảo sát ngữ cảnh trước khi nhảy vào code |
| `endpoint-change-check.md` | Trước khi thêm/sửa endpoint | Checklist contract, validation, test |
| `security-change-check.md` | Trước khi thay đổi auth/endpoint | Checklist auth, public surface, actuator |
| `post-edit-java.md` | Sau khi sửa file Java | Checklist lỗi phổ biến: phân lớp, null, transaction |
| `migration-safety.md` | Trước khi sửa SQL/Flyway | Checklist migration safety, tương thích ngược |
| `pre-commit-review.md` | Trước khi commit | Checklist tổng hợp: đúng đắn, kiến trúc, test |

## Quy tắc

- Mỗi hook chỉ là checklist → đọc, kiểm tra, kết luận
- Không đưa logic nghiệp vụ mới vào hook
- Hook được kích hoạt tự động qua `settings.json` (pre-edit, post-edit, pre-commit)
- Hook không can thiệp vào quá trình code — chỉ nhắc nhở trước/sau

## Hook gợi ý trong bộ này

- `pre-implement.md`
- `post-edit-java.md`
- `pre-commit-review.md`
- `migration-safety.md`
- `security-change-check.md`
- `endpoint-change-check.md`
