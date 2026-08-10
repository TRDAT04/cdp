# Quy tắc phong cách code

Áp dụng các quy tắc này cho mọi code Java và Spring.

## Chung
- Theo convention package và đặt tên hiện có của repo
- Ưu tiên code dễ đọc hơn code khéo léo
- Giữ thay đổi nhỏ và tập trung
- Tránh đưa pattern mới nếu không có lý do rõ ràng

## Class và method
- dùng tên thể hiện rõ ý định
- giữ method tập trung
- ưu tiên ngôn ngữ nghiệp vụ rõ ràng
- tránh helper/util mơ hồ
- tránh god class

## Dependency injection
- ưu tiên constructor injection
- không dùng field injection trừ khi repo đã chuẩn hóa như vậy

## Exceptions
- dùng exception cụ thể
- mapping exception qua global error handling của repo
- không nuốt exception
- tránh `catch (Exception)` trừ khi thực sự cần

## Transactions
- định nghĩa transaction ở ranh giới service
- không để giả định transaction bị lan mơ hồ qua các tầng
- giữ phần việc trong transaction đủ chặt chẽ

## Mapping
- không lặp mapping logic ở nhiều nơi
- tái sử dụng chiến lược mapper hiện có
- không trộn entity và response model tùy tiện

## Logging
- log có chủ đích
- có kèm context nghiệp vụ hữu ích
- tránh log quá ồn
- tránh log dữ liệu nhạy cảm

## JSON naming convention

- Dùng **camelCase** cho toàn bộ JSON field trong request/response API
- Entity field giữ theo convention Java (camelCase), map qua mapper
- Đặt `@JsonProperty("...")` hoặc dùng mapper config nếu API consumer yêu cầu snake_case
- KHÔNG mix camelCase và snake_case trong cùng một response

## Equals / HashCode

- Dùng Lombok `@EqualsAndHashCode` với `onlyExplicitlyIncluded = true` cho JPA entity
- KHÔNG dùng `callSuper = true` cho entity (performance issue với JPA)
- Với DTO/record: để default hoặc dùng `@EqualsAndHashCode` không tham số
- KHÔNG tự implement equals/hashCode dựa trên DB-generated ID — dùng business key nếu cần so sánh

## Builder pattern

- Dùng Lombok `@Builder` cho entity và DTO khi cần tạo object với nhiều field tùy chọn
- Dùng **record** cho immutable request DTO nhỏ (10 field trở xuống)
- KHÔNG dùng builder cho response DTO có format cố định

## Optional usage

- Dùng `Optional<T>` cho method return có thể null thay vì trả null
- KHÔNG dùng `Optional` làm field type hoặc method parameter — dùng null hoặc overload
- Dùng `orElse()`, `orElseGet()`, `orElseThrow()` — KHÔNG unwrap bằng `.get()` mà không có fallback
- Với collection return: dùng empty collection thay vì `Optional<Collection<T>>`

## Checklist review
Trước khi chốt:
- tên có nhất quán không
- phân lớp có đúng không
- có lặp code không
- exception đã xử lý nhất quán chưa
- log có phù hợp không
