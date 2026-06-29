package vn.vnpost.cdp.unomi.dto;

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
public class UnomiResponse {

    private Boolean success;
    private String message;
    private Object data;
}
