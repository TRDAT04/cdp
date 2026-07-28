# Hook: security-change-check

Mục tiêu:
Bắt buộc rà soát khi có thay đổi liên quan bảo mật.

## Bắt buộc kiểm tra
1. Authorization
- endpoint mới có được bảo vệ đúng không
- method security có cần thêm không
- role/authority mapping có đúng không

2. Public surface
- có endpoint nào vô tình public không
- matcher ordering có làm hở route không
- actuator có mở quá mức không
