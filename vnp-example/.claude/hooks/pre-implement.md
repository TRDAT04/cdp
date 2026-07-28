# Hook: pre-implement

Mục tiêu:
Trước khi agent bắt đầu sửa code, phải khảo sát đủ ngữ cảnh để tránh sửa mù.

## Bắt buộc thực hiện trước khi sửa code
1. Đọc:
- CLAUDE.md
- rules liên quan đến task hiện tại

2. Kiểm tra codebase:
- file/controller/service/repository/entity/dto/test tương tự
- config chung liên quan
- global exception handler nếu có
- cấu trúc response chuẩn nếu có

3. Trước khi sửa code phải nêu rõ:
- mục tiêu thay đổi
- các file dự kiến bị ảnh hưởng
- có đổi API contract không
- có đổi DB/schema/query không
- có rủi ro bảo mật không
- test nào sẽ cần chạy hoặc thêm
