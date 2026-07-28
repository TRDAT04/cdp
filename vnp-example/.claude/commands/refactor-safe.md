# Lệnh: refactor-safe

Mục tiêu:
Refactor an toàn, giữ nguyên hành vi, giữ nguyên contract công khai và transaction semantics trừ khi được yêu cầu khác.

## Cách làm việc
Khi refactor, phải làm theo thứ tự:

1. Đọc trước:
- CLAUDE.md
- coding-style.md
- testing.md
- api-design.md nếu có chạm vào API
- database.md nếu có chạm query/entity/persistence

2. Khảo sát:
- xác định phần code bị lặp, khó đọc, hoặc sai phân lớp
- xác định public behavior nào phải giữ nguyên
- xác định test nào đang bảo vệ hành vi hiện có

3. Trước khi refactor, phải nêu:
- mục tiêu refactor là gì
- hành vi nào phải giữ nguyên
- file nào bị ảnh hưởng
- rủi ro lớn nhất là gì
- test nào sẽ chứng minh không đổi hành vi
