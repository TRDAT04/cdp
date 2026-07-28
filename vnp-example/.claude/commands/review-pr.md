# Lệnh: review-pr

Mục tiêu:
Review diff hoặc pull request theo checklist chuẩn của backend Spring Boot, tập trung vào tính đúng đắn, kiến trúc, bảo mật, DB, hiệu năng và test.

## Cách làm việc
Khi review diff, hãy đọc trước:
- CLAUDE.md
- review-checklist.md
- security.md
- database.md nếu diff có migration/query/entity
- api-design.md nếu có thay đổi endpoint
- testing.md nếu có thay đổi test hoặc thiếu test

## Cách trả kết quả
Phân loại theo mức độ:
- Critical
- High
- Medium
- Low
- Suggestion

Mỗi nhận xét nên có:
- vị trí: file/class/method nếu có thể
- vấn đề là gì
- vì sao quan trọng
- gợi ý sửa cụ thể
