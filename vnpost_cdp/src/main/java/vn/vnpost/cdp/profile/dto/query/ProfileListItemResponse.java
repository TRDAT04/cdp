package vn.vnpost.cdp.profile.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileListItemResponse {
    // ---- Dữ liệu từ PostgreSQL (MasterProfile) ----
    private Long id;
    private String fullName;
    private String avatarText;
    private String profileCode;
    private String phone;
    private String email;
    private String customerType;
    private String customerTypeText;
    private String warningStatus;
    private String warningText;
    private List<String> sourceSystems;
    private LocalDateTime lastActivityAt;
    private Short status;
    private String statusText;

    // ---- Dữ liệu hành vi từ Apache Unomi ----


    @Builder.Default
    private List<String> segments = Collections.emptyList();

    private Instant firstVisit;

    private Instant previousVisit;

    private Instant lastVisit;

    private Integer nbOfVisits;

    private Integer purchaseCount;

    private BigDecimal totalSpent;

    private Instant lastTransactionDate;
}

