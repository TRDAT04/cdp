package vn.vnpost.cdp.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodResult {

    private boolean success;
    private String message;
    private Object data;
    private boolean status;
    private Long totalRecord;

    public static MethodResult success() {
        return MethodResult.builder()
                .success(true)
                .status(true)
                .build();
    }

    public static MethodResult success(Object data) {
        return MethodResult.builder()
                .success(true)
                .status(true)
                .data(data)
                .build();
    }

    public static MethodResult success(Object data, Long totalRecord) {
        return MethodResult.builder()
                .success(true)
                .status(true)
                .data(data)
                .totalRecord(totalRecord)
                .build();
    }

    public static MethodResult error(String message) {
        return MethodResult.builder()
                .success(false)
                .status(false)
                .message(message)
                .build();
    }
}