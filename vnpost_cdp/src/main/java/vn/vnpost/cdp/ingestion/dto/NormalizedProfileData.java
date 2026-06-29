package vn.vnpost.cdp.ingestion.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedProfileData {
    private String sourceSystem;
    private String sourceCustomerId;
    private String fullName;
    private String phone;
    private String email;
    private String identityNo;
    private String gender;
    private LocalDate dateOfBirth;
    private String customerType;
    private String provinceCode;
    private String provinceName;
    private String unitCode;
    private String unitName;
    private List<String> interestedServices;
    private LocalDateTime lastVisitAt;
    private Map<String, Object> normalizedPayload;
}
