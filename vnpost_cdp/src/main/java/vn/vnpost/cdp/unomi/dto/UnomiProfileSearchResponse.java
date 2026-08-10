package vn.vnpost.cdp.unomi.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnomiProfileSearchResponse {

    private List<UnomiProfileResponse> list;

    private Integer offset;

    private Integer pageSize;

    private Integer totalSize;

    private String totalSizeRelation;
}
