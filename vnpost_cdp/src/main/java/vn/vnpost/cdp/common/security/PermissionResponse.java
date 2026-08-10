package vn.vnpost.cdp.common.security;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PermissionResponse {
    boolean success;
    String error;
    String message;
    Integer status;
    Object data;
    Long totalRecord;
}

