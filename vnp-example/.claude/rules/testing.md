# Quy tắc kiểm thử

Áp dụng các quy tắc này mỗi khi thay đổi code production.

## Chung
- Mọi thay đổi code có ý nghĩa đều phải có test đi kèm hoặc có lý do rõ ràng
- Ưu tiên loại test nhỏ nhất nhưng vẫn đủ độ tin cậy
- Tái sử dụng pattern test và utility test đang có

## Unit test
Dùng unit test cho:
- logic nghiệp vụ
- logic validate
- chuyển trạng thái
- orchestration trong service

Kỳ vọng:
- setup rõ ràng
- mock tối thiểu
- assertion tập trung vào hành vi

## Integration test
Dùng integration test cho:
- hành vi của database
- query repository
- transaction
- wiring của endpoint
- serialization/deserialization
- hành vi security filter nếu liên quan

## Sửa bug
- Nếu có thể, thêm regression test trước
- Test đó phải fail trước khi fix và pass sau khi fix

## Chất lượng test
Ưu tiên:
- tên test mô tả rõ ý nghĩa
- cấu trúc arrange-act-assert
- assertion có ý nghĩa
- dữ liệu xác định
- độ phức tạp phụ ít nhất có thể

Tránh:
- phụ thuộc thời gian gây dễ vỡ nếu không kiểm soát
- mock quá nhiều
- test chi tiết nội bộ của framework
- assertion vào các chi tiết cài đặt không quan trọng

## Testcontainers

Dùng Testcontainers cho integration test cần database thật.

### Cấu hình base class

```java
@SpringBootTest
@Testcontainers
class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
```

### Quy tắc

- Mỗi test class có container riêng → tránh tranh chấp giữa các test class
- Nếu repo dùng shared container (Jupiter `@Shared`), đặt `@TestInstance(Lifecycle.PER_CLASS)` để tránh race condition
- Dùng `@ServiceConnection` (Spring Boot 3.1+) thay vì `DynamicPropertySource` nếu có thể
- KHÔNG dùng `@DirtiesContext` trừ khi thực sự cần — làm chậm test đáng kể

### Seed data

- Dùng Flyway migration test-only (đặt trong `src/test/resources/db/migration`)
- Hoặc dùng `@BeforeEach` với repository để seed dữ liệu deterministic
- KHÔNG seed dữ liệu bằng raw SQL trong test body nếu có migration sẵn
- Mỗi test method nên tạo data riêng (isolation), tránh phụ thuộc giữa các test

### Tránh làm chậm CI

- Chạy test có container song song với `--tests` phân tách nếu CI cho phép
- Container reuse: dùng `testcontainers.org` singleton nếu CI có quyền Docker daemon
- Integration test vs unit test: chỉ chạy integration test khi có thay đổi liên quan (`mvn verify -DskipITs` mặc định, `-DskipITs=false` khi cần)

## Checklist review
Trước khi chốt:
- các nhánh thay đổi đã được cover chưa
- test cũ còn hợp lệ không
- các nhánh rủi ro đã được cover chưa
- edge case đã được xem xét chưa
- nếu là bug fix thì đã có test tái hiện chưa
- testcontainers setup có đúng lifecycle không
- seed data có deterministic và isolated không
