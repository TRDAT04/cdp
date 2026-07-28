package vn.vnpost.example.unomi.dto;



import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnomiSegmentResponse {
    private String id;
    private String name;
    private String scope;
    private List<String> tags;
    private List<String> systemTags;
    private Boolean enabled;
    private Boolean missingPlugins;
    private Boolean hidden;
    private Boolean readOnly;
}
