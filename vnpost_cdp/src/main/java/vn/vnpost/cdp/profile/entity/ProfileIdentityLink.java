package vn.vnpost.cdp.profile.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import vn.vnpost.cdp.common.entity.BaseAuditFields;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Table("profile_identity_links")
public class ProfileIdentityLink extends BaseAuditFields {

    @Id
    @Column("id")
    private Long id;

    @Column("master_profile_id")
    private Long masterProfileId;

    @Column("source_system")
    private String sourceSystem;

    @Column("source_customer_id")
    private String sourceCustomerId;

    @Column("identity_type")
    private String identityType;

    @Column("identity_value")
    private String identityValue;

    @Column("confidence_score")
    private BigDecimal confidenceScore;

    @Column("is_primary")
    private Boolean isPrimary;

    @Column("status")
    private Short status = 1;

    @Column("linked_at")
    private LocalDateTime linkedAt;

    @Column("linked_by")
    private String linkedBy;
}
