package vn.vnpost.cdp.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.vnpost.cdp.common.response.MethodResult;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<MethodResult> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: [{}] {}", ex.getErrorCode(), ex.getMessage());

        HttpStatus status = switch (ex.getErrorCode()) {
            case "RULE_VALIDATION_ERROR" -> HttpStatus.UNPROCESSABLE_ENTITY;  // 422
            case "RULE_DEPLOY_ERROR"     -> HttpStatus.BAD_GATEWAY;           // 502
            default                      -> HttpStatus.BAD_REQUEST;           // 400
        };

        return ResponseEntity
                .status(status)
                .body(MethodResult.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MethodResult> handleValidationException(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation exception: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(MethodResult.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MethodResult> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MethodResult.error("An unexpected error occurred. Please try again later."));
    }
}
