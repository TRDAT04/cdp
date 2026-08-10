# Hook: endpoint-change-check

Mục tiêu:
Mỗi khi thêm hoặc sửa endpoint, phải kiểm tra contract, validation, security và test.

## Bắt buộc kiểm tra
1. Contract
- path có đúng convention không
- request DTO có rõ ràng không
- response có đúng format chuẩn không
- có breaking change không

2. Validation
- input đã được validate chưa
- enum/filter/sort/page có được validate không
- trường bắt buộc đã được đánh dấu chưa
