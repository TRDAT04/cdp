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

    public static MethodResult success() {
        return MethodResult.builder()
                .success(true)
                .message("Success")
                .build();
    }

    public static MethodResult success(Object data) {
        return MethodResult.builder()
                .success(true)
                .message("Success")
                .data(data)
                .build();
    }

    public static MethodResult error(String message) {
        return MethodResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
