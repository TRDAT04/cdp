# Hook: pre-commit-review

Mục tiêu:
Trước khi coi thay đổi là hoàn tất, phải thực hiện một vòng rà soát cuối.

## Bắt buộc kiểm tra
1. Tính đúng đắn
- code có đúng yêu cầu không
- có thay đổi hành vi ngoài phạm vi không
- edge case quan trọng đã được xử lý chưa

2. Kiến trúc
- đúng phân lớp controller -> service -> repository chưa
- có helper/util mới nào dư thừa không
- có pattern mới không cần thiết không
