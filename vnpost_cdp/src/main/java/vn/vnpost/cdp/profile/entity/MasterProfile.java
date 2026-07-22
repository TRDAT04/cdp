package vn.vnpost.cdp.profile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vnpost.cdp.common.entity.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "master_profiles")
public class MasterProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "profile_code", nullable = false, unique = true, length = 100)
    private String profileCode;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "identity_no", length = 100)
    private String identityNo;

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(name = "customer_type", length = 100)
    private String customerType;

    @Column(name = "customer_tier", length = 50)
    private String customerTier;

    @Column(name = "customer_group", length = 50)
    private String customerGroup;

    @Column(name = "province_code", length = 50)
    private String provinceCode;

    @Column(name = "province_name", length = 255)
    private String provinceName;

    @Column(name = "unit_code", length = 50)
    private String unitCode;

    @Column(name = "unit_name", length = 255)
    private String unitName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_summary", columnDefinition = "jsonb")
    private Map<String, Object> sourceSummary;

    @Column(name = "last_merged_at")
    private LocalDateTime lastMergedAt;

    @Column(name = "synced_to_unomi_at")
    private LocalDateTime syncedToUnomiAt;

    @Column(name = "merged_into_profile_id")
    private Long mergedIntoProfileId;

    @Column(name = "status", nullable = false)
    private Short status = 1;
}
