# Checklist review

Dùng checklist này khi review hoặc sinh code. Với mỗi mục, kiểm tra bằng **cách đọc code cụ thể** — không chỉ hỏi "có đúng không" mà phải xác nhận bằng evidence trong code.

## Tính đúng đắn
- [ ] Implementation đáp ứng đúng yêu cầu
- [ ] Edge case: null input, empty list, concurrent update, boundary value
- [ ] Validation rule khớp spec
- **Cách kiểm tra**: đọc method, trace các branch if/else, kiểm tra null/empty handling

## Kiến trúc
- [ ] Controller không chứa if/else nghiệp vụ, không gọi EntityManager, không truy vấn trực tiếp
- [ ] Service chứa logic nghiệp vụ thuần túy
- [ ] DTO và entity tách riêng, không return JPA entity trực tiếp từ controller
- [ ] Không có helper/util class mới trùng chức năng với cái đã có
- **Cách kiểm tra**: đọc mỗi method trong controller — nếu có logic tính toán/điều kiện nghiệp vụ → vi phạm

## API
- [ ] Path, HTTP method, status code nhất quán với convention hiện có
- [ ] Request DTO có `@Valid` và đầy đủ constraint
- [ ] Response theo format chuẩn (ApiResponse / wrapper hiện có)
- [ ] Lỗi trả về qua GlobalExceptionHandler, không throw trực tiếp ra controller
- [ ] Không có breaking change (field rename, type change, status code change)
- **Cách kiểm tra**: so sánh với controller/DTO cùng resource hiện có

## Database
- [ ] Migration mới (nếu có) không sửa migration cũ đã áp dụng
- [ ] Schema change additive trước (thêm column → rename/remove)
- [ ] Index đã được tạo cho filter/sort field mới
- [ ] Query không có N+1 (dùng JOIN FETCH hoặc EntityGraph)
- [ ] Không dùng native query nếu JPQL đủ
- **Cách kiểm tra**: đọc migration file, chạy `EXPLAIN` cho query mới, kiểm tra fetch strategy

## Bảo mật
- [ ] Endpoint mới có `@PreAuthorize` hoặc equivalent nếu cần
- [ ] Input được validate (constraint annotation hoặc manual check)
- [ ] Không log password/token/secret/PII
- [ ] Không có SQL/JPQL injection (dùng parameter binding)
- [ ] Actuator / health endpoint không exposed public
- **Cách kiểm tra**: kiểm tra security config, grep các pattern như `"%" + var`, tìm credential trong log

## Hiệu năng
- [ ] Không có N+1 query
- [ ] Không gọi DB/remote trong vòng lặp
- [ ] Phân trang cho query trả về list lớn
- [ ] Không load toàn bộ entity graph khi chỉ cần vài field
- **Cách kiểm tra**: chạy integration test với dữ liệu lớn hoặc review query plan

## Kiểm thử
- [ ] Logic nghiệp vụ mới có unit test
- [ ] Endpoint mới có API test (MockMvc hoặc RestAssured)
- [ ] Bug fix có regression test (fail trước fix, pass sau fix)
- [ ] Test không phụ thuộc thời gian hoặc trạng thái toàn cục
- **Cách kiểm tra**: đọc test class, kiểm tra test có assert thực sự (không chỉ assert không throw)

## Khả năng bảo trì
- [ ] Tên method/class mô tả đúng ý nghĩa nghiệp vụ
- [ ] Không lặp code (kiểm tra duplicate logic giữa các method/class)
- [ ] Diff tập trung, không trộn refactor với thay đổi hành vi
- **Cách kiểm tra**: kiểm tra method length (< 30-40 dòng là signal tốt), grep duplicate pattern

## Kết quả cuối
Phân loại nhận xét theo:
- **Critical** — bug tiềm ẩn, security gap, breaking change
- **High** — vi phạm kiến trúc, N+1, thiếu test cho logic quan trọng
- **Medium** — lặp code, naming không rõ, thiếu validation edge case
- **Low** — style inconsistency, logging ồn
- **Suggestion** — cải thiện khả năng đọc, tách method
