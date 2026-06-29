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
public class DataResponse<T> {

    private boolean success;
    private String message;
    private T data;

    public static <T> DataResponse<T> success(T data) {
        return DataResponse.<T>builder()
                .success(true)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> DataResponse<T> error(String message) {
        return DataResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
