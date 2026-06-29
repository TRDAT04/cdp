package vn.vnpost.cdp.profile.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileSourceSystemResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String sourceType;
    private Integer priority;
    private Short status;
    private String createdBy;
    private LocalDateTime created;
    private LocalDateTime modified;
    private String modifiedBy;
}
