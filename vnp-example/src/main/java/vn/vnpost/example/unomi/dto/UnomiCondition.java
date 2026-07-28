package vn.vnpost.example.unomi.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnomiCondition {

    private String type;

    private Map<String, Object> parameterValues;
}
