package vn.vnpost.example.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileIdentityLinkDetailResponse {
    private Long id;
    private String sourceSystem;
    private String sourceCustomerId;
    private String identityType;
    private String identityValue;
    private BigDecimal confidenceScore;
    /** Nhãn mức độ tin cậy: "Cao" (≥90), "Trung bình" (70–89), "Thấp" (<70). */
    private String confidenceLevel;
    private Boolean isPrimary;
    private Short status;
    private String statusText;
    private LocalDateTime linkedAt;
    private String linkedBy;
}
