# Quy tắc xử lý lỗi

Áp dụng khi thêm, sửa exception class hoặc thay đổi global exception handler.

## Exception class convention

Mỗi business exception tạo class riêng, kế thừa từ một base class chung.

```java
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) { ... }
}

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resource, Object id) {
        super("RESOURCE_NOT_FOUND",
              String.format("%s with id=%s not found", resource, id),
              HttpStatus.NOT_FOUND);
    }
}
```

Quy tắc:
- Đặt tên theo pattern `{Situation}Exception`
- Có `code` — dùng cho API response và logging (không dùng exception class name làm code)
- Có `status` — map trực tiếp sang HTTP status
- KHÔNG dùng `Exception` chung cho business logic
- KHÔNG throw `IllegalArgumentException` từ service — dùng `ValidationException`

## Exception thường dùng

| Exception | HTTP Status | Dùng khi |
|---|---|---|
| `ResourceNotFoundException` | 404 | entity không tìm thấy |
| `ValidationException` | 400 | request param/body không hợp lệ |
| `ConflictException` | 409 | duplicate, state conflict |
| `ForbiddenException` | 403 | không có quyền |
| `UnauthorizedException` | 401 | chưa authenticate |
| `InternalException` | 500 | lỗi hệ thống không mong đợi |

## GlobalExceptionHandler pattern

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException ex) {
        log.warn("Business error: code={}, message={}", ex.getCode(), ex.getMessage());
        return ApiResponse.error(ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<List<FieldError>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
            .toList();
        return ApiResponse.error("Validation failed", "VALIDATION_ERROR", errors);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ApiResponse.error("Internal server error", "INTERNAL_ERROR");
    }
}
```

## Error response format

```json
// Thành công
{ "success": true, "data": {...}, "message": null }

// Lỗi business
{ "success": false, "data": null, "message": "User not found" }

// Lỗi validation
{ "success": false, "data": [{ "field": "email", "message": "must be valid email" }], "message": "Validation failed" }
```

Quy tắc:
- KHÔNG expose stack trace trong response
- KHÔNG expose SQL error detail
- KHÔNG expose internal ID (entity ID, correlation ID nội bộ) trừ khi được log riêng
- `message` dùng cho end user — phải đọc được, không kỹ thuật
- Có `code` cho client code xử lý theo logic (không bắt message string)

## Logging convention cho exception

```java
// Business exception — WARN, có context
log.warn("Order not found: orderId={}, userId={}", orderId, userId);

// Unexpected exception — ERROR, có stack trace
log.error("Failed to process payment", ex);

// Validation — DEBUG hoặc WARN tùy mức nghiêm trọng
log.debug("Invalid input: field={}, value={}", field, value);
```

## Checklist review

Trước khi chốt:
- exception class có code và status rõ ràng không
- KHÔNG throw generic Exception/RuntimeException từ service
- GlobalExceptionHandler đã handle đúng exception type chưa
- error response format nhất quán với project không
- stack trace / SQL detail / internal ID không lộ qua API
- logging có đủ context nhưng không log dữ liệu nhạy cảm