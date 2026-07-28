# Quy tắc thiết kế API

Áp dụng các quy tắc này khi tạo mới hoặc sửa endpoint REST.

## Quy ước endpoint
- Làm theo quy ước đặt path hiện có trong repo
- Ưu tiên path theo resource
- Không trộn nhiều kiểu đặt tên giữa các controller
- Tái sử dụng chiến lược versioning hiện có

## Request/response
- Dùng request DTO để validate input
- Dùng response DTO hoặc response object chuẩn của project
- Không expose entity persistence nội bộ trực tiếp trừ khi repo đã chuẩn hóa như vậy
- Giữ tương thích ngược nếu có thể

## Validation
- Validate toàn bộ input từ bên ngoài
- Dùng annotation Jakarta Bean Validation khi phù hợp
- Đảm bảo enum hoặc filter parameter được validate hoặc parse an toàn
- Trả lỗi validation qua handler chuẩn của project

## Status code
Dùng ngữ nghĩa HTTP nhất quán:
- 200 cho đọc/cập nhật thành công khi phù hợp
- 201 cho tạo mới nếu project đang theo kiểu đó
- 204 cho xóa thành công không trả nội dung khi phù hợp
- 400 cho request không hợp lệ
- 401 cho chưa xác thực
- 403 cho không có quyền
- 404 cho không tìm thấy resource
- 409 cho conflict khi liên quan
- 422 chỉ dùng nếu repo đã dùng nhất quán

## Xử lý lỗi
- Tái sử dụng cơ chế global exception handling
- Không làm lộ stack trace hoặc chi tiết nội bộ
- Message cần hữu ích nhưng an toàn

## Phân trang/lọc/sắp xếp
- Theo chuẩn object phân trang và tên param hiện có của repo
- Validate sort field nếu có hỗ trợ sort tùy biến
- Tránh query không giới hạn

## Checklist review
Trước khi chốt thay đổi API, kiểm tra:
- tính nhất quán của contract
- validate đã đủ chưa
- auth/authz đúng chưa
- cấu trúc response có nhất quán không
- có breaking change không
- test API đã cập nhật chưa
