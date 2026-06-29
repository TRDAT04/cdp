package vn.vnpost.cdp.profile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterProfileResponse {

    private Long id;
    private String profileCode;
    private String fullName;
    private String phone;
    private String email;
    private String gender;
    private LocalDate dateOfBirth;
    private String identityNo;
    private String customerType;
    private String provinceCode;
    private String provinceName;
    private String unitCode;
    private String unitName;
    private Map<String, Object> sourceSummary;
    private LocalDateTime lastMergedAt;
    private LocalDateTime syncedToUnomiAt;
    private Long mergedIntoProfileId;
    private Short status;

    // Audit fields from BaseEntity
    private String createdBy;
    private LocalDateTime created;
    private LocalDateTime modified;
    private String modifiedBy;
}
