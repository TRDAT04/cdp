package vn.vnpost.cdp.profile.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import vn.vnpost.cdp.common.entity.BaseAuditFields;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Table("master_profiles")
public class MasterProfile extends BaseAuditFields {

    @Id
    @Column("id")
    private Long id;

    @Column("profile_code")
    private String profileCode;

    @Column("full_name")
    private String fullName;

    @Column("phone")
    private String phone;

    @Column("email")
    private String email;

    @Column("gender")
    private String gender;

    @Column("date_of_birth")
    private LocalDate dateOfBirth;

    @Column("identity_no")
    private String identityNo;

    @Column("tax_code")
    private String taxCode;

    @Column("customer_type")
    private String customerType;

    @Column("customer_tier")
    private String customerTier;

    @Column("customer_group")
    private String customerGroup;

    @Column("province_code")
    private String provinceCode;

    @Column("province_name")
    private String provinceName;

    @Column("unit_code")
    private String unitCode;

    @Column("unit_name")
    private String unitName;

    @Column("source_summary")
    private Map<String, Object> sourceSummary;

    @Column("last_merged_at")
    private LocalDateTime lastMergedAt;

    @Column("synced_to_unomi_at")
    private LocalDateTime syncedToUnomiAt;

    @Column("merged_into_profile_id")
    private Long mergedIntoProfileId;

    @Column("status")
    private Short status = 1;
}
