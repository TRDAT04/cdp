# Lệnh: implement-endpoint

Mục tiêu:
Triển khai hoặc chỉnh sửa một endpoint Spring Boot theo đúng kiến trúc của repo, có xét đến API contract, validation, service logic, persistence, bảo mật và test.

## Cách làm việc
Khi nhận yêu cầu triển khai endpoint, luôn làm theo thứ tự:

1. Đọc trước:
- CLAUDE.md
- các rules liên quan:
  - api-design.md
  - coding-style.md
  - security.md
  - testing.md
  - database.md nếu có thay đổi DB

2. Khảo sát codebase:
- tìm controller tương tự
- tìm service tương tự
- tìm request/response DTO tương tự
- tìm pattern validate hiện có
- tìm global exception handler
- tìm cách phân trang/lọc/sắp xếp hiện tại
- tìm test tương tự

3. Trước khi sửa code, phải trả lời ngắn gọn:
- endpoint nào sẽ được thêm/sửa
- file nào sẽ bị ảnh hưởng
- có đổi contract không
- có đổi DB không
- có rủi ro bảo mật gì không
- kế hoạch test là gì

4. Chỉ sau đó mới bắt đầu chỉnh sửa.

## Quy tắc bắt buộc
- Không đặt logic nghiệp vụ trong controller
- Không gọi repository trực tiếp từ controller
- Request phải được validate
- Response phải theo convention hiện có của repo
- Phải dùng cơ chế xử lý lỗi chuẩn của project
- Không làm lộ thông tin nội bộ
- Không đổi contract âm thầm
- Nếu có thay đổi DB, phải kiểm tra migration trước

## Đầu ra mong muốn
Khi hoàn thành, phải cung cấp:
1. Tóm tắt thay đổi
2. Danh sách file đã sửa/thêm
3. Contract API đã thay đổi gì
4. Test đã thêm/cập nhật
5. Rủi ro còn lại
6. Việc tiếp theo nếu có
