package vn.vnpost.example.ingestion.dto;

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
    /** Loại event nghiệp vụ, ví dụ PROFILE_CREATED / PROFILE_ENRICHED. */
    private String eventType;
    private String fullName;
    private String phone;
    private String email;
    private String identityNo;
    /** Mã số thuế (MST) — hard field trên master_profiles, tách khỏi identityNo. */
    private String taxCode;
    private String gender;
    private LocalDate dateOfBirth;
    private String customerType;
    private String customerTier;
    private String provinceCode;
    private String provinceName;
    private String unitCode;
    private String unitName;

    // --- Định danh liên nguồn (enrichment) -> profile_identity_links ---
    private String postId;
    private String crmId;
    private String khlCode;
    private String appUserId;
    private String deviceId;
    private String cookieId;
    private String paymentId;

    private List<String> interestedServices;
    private LocalDateTime lastVisitAt;
    private Map<String, Object> normalizedPayload;
}
