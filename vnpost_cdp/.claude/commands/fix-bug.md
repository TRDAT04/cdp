# Lệnh: fix-bug

Mục tiêu:
Phân tích và sửa bug theo cách an toàn nhất, với thay đổi nhỏ nhất có thể, có regression test nếu phù hợp.

## Cách làm việc
Khi nhận yêu cầu sửa bug, luôn làm theo thứ tự:

1. Đọc trước:
- CLAUDE.md
- testing.md
- coding-style.md
- security.md nếu bug liên quan auth, input, dữ liệu nhạy cảm
- database.md nếu bug liên quan query, transaction, migration, persistence

2. Phân tích bug:
- xác định đường tái hiện
- tìm class/method liên quan
- tìm module tương tự nếu cần
- nêu nguyên nhân gốc khả dĩ
- phân biệt triệu chứng và nguyên nhân gốc

3. Trước khi sửa code, phải nêu:
- nguyên nhân gốc dự kiến
- file/class bị ảnh hưởng
- bản sửa nhỏ nhất nhưng an toàn
- test cần thêm để tái hiện bug

4. Nếu có thể:
- viết regression test trước
- sau đó mới vá code

5. Sau khi sửa:
- chạy test liên quan
- nêu rõ bug đã được chặn bằng test nào

## Quy tắc bắt buộc
- Không refactor lớn khi chỉ sửa bug nhỏ, trừ khi được yêu cầu
- Không đổi hành vi không liên quan
- Không trộn nhiều thay đổi vào cùng một lần sửa
- Ưu tiên thay đổi tối thiểu nhưng đúng bản chất
- Nếu không chắc nguyên nhân gốc, phải nói rõ mức độ chắc chắn

## Đầu ra mong muốn
1. Mô tả nguyên nhân gốc
2. Mô tả bản vá
3. Danh sách file thay đổi
4. Regression test nào đã được thêm
5. Những rủi ro hoặc giả định còn lại
