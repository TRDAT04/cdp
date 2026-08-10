# Quy tắc bảo mật

Áp dụng các quy tắc này cho mọi thay đổi backend, đặc biệt là auth, xử lý file, query builder và tích hợp hệ thống ngoài.

## Luôn kiểm tra
- thiếu phân quyền
- lỗi kiểm soát truy cập
- truy cập đối tượng không an toàn
- thiếu validate input
- SQL/JPQL/native query injection
- xử lý file upload không an toàn
- lộ secret
- lộ chi tiết lỗi quá mức
- redirect hoặc callback URL không an toàn
- cấu hình mặc định không an toàn

## Kiểm tra riêng cho Spring Security
- thứ tự matcher
- endpoint public
- mapping role/authority
- tính nhất quán với method security
- actuator exposure
- ảnh hưởng của CORS/CSRF
- giả định về session/stateful hay stateless

## Logging và secrets
- không bao giờ log token, password, API key, secret
- không log dữ liệu cá nhân nhạy cảm trừ khi có yêu cầu rõ và đã được kiểm soát
- sanitize exception message nếu cần

## Tích hợp hệ thống ngoài
- validate dữ liệu gửi ra ngoài
- xử lý timeout và failure an toàn
- không mặc định tin tưởng hệ thống downstream
- xem xét idempotency và replay risk nếu liên quan

## Checklist review
Trước khi chốt:
- auth/authz đã được xác minh chưa
- input do user điều khiển đã được validate chưa
- có lộ dữ liệu nhạy cảm không
- security test đã cập nhật nếu liên quan chưa
