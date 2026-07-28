package vn.vnpost.example.unomi.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnomiSegmentDetailResponse {

    private String itemId;

    private String itemType;

    private Integer version;

    private Map<String, Object> condition;

    private Map<String, Object> metadata;

}
