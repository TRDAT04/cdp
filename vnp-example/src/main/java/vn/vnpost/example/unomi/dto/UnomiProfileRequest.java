package vn.vnpost.example.unomi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnomiProfileRequest {

    private String itemId;
    private String itemType;
    private Map<String, Object> properties;
}
