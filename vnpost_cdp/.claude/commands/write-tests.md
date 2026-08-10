# Lệnh: write-tests

Mục tiêu:
Thêm hoặc cập nhật test đúng loại, đủ mức tin cậy, bám theo pattern test hiện có trong repo.

## Cách làm việc
Khi được yêu cầu viết test, hãy:

1. Đọc trước:
- CLAUDE.md
- testing.md
- coding-style.md
- security.md nếu test liên quan auth/authz
- database.md nếu test liên quan persistence

2. Xác định loại test phù hợp:
- unit test cho logic nghiệp vụ
- slice test cho controller/repository khi phù hợp
- integration test cho endpoint, transaction, DB behavior
- regression test cho bug fix

3. Kiểm tra pattern test hiện có:
- cách đặt tên test
- utility/common fixture
- mock strategy
- testcontainers setup
- base test class nếu có

4. Trước khi viết test, phải nêu:
- sẽ viết loại test nào
- vì sao loại đó phù hợp
- phạm vi được cover là gì
- điều gì chưa cover
