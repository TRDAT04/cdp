package vn.vnpost.cdp.unomi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnomiProfileSearchResponse {

    private List<UnomiProfileResponse> list;

    private Integer offset;

    private Integer pageSize;

    private Integer totalSize;

    private String totalSizeRelation;
}