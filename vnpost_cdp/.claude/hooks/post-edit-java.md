# Hook: post-edit-java

Mục tiêu:
Sau khi sửa file Java hoặc các file chính của backend, phải rà lại các lỗi phổ biến trước khi đi tiếp.

## Bắt buộc kiểm tra sau khi sửa
1. Kiến trúc
- có logic nghiệp vụ bị đặt nhầm vào controller không
- có repository bị gọi sai tầng không
- có lặp code mới không

2. Tính đúng đắn
- validate input đã đủ chưa
- null handling có an toàn không
- exception đã được xử lý nhất quán chưa
- transaction boundary có bị thay đổi không chủ đích không
