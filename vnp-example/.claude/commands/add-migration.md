# Lệnh: add-migration

Mục tiêu:
Thêm migration an toàn cho Spring Boot + Flyway, có xét đến tương thích ngược, dữ liệu hiện có, index và rollout.

## Cách làm việc
Khi thêm migration, phải làm theo thứ tự:

1. Đọc trước:
- CLAUDE.md
- database.md
- testing.md nếu có integration test DB

2. Kiểm tra hiện trạng:
- entity hiện có
- schema hiện có
- repository/query hiện có
- migration trước đó
- nơi code sẽ sử dụng cột/bảng mới

3. Trước khi tạo migration, phải nêu:
- mục tiêu schema change
- file bị ảnh hưởng
- có phá vỡ tương thích ngược không
- cần backfill không
- cần index không
- có rủi ro lock bảng không
- cách rollout an toàn

4. Tạo migration theo nguyên tắc:
- không sửa migration cũ
- đặt tên rõ nghĩa
- ưu tiên thay đổi cộng thêm trước
- nếu thay đổi nguy hiểm, nêu rõ rủi ro và phương án giảm thiểu
