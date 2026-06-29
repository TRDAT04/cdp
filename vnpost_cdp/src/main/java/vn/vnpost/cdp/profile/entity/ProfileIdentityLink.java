package vn.vnpost.cdp.profile.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.vnpost.cdp.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "profile_identity_links")
public class ProfileIdentityLink extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "master_profile_id", nullable = false)
    private Long masterProfileId;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "source_customer_id", length = 255)
    private String sourceCustomerId;

    @Column(name = "identity_type", length = 100)
    private String identityType;

    @Column(name = "identity_value", length = 500)
    private String identityValue;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "is_primary")
    private Boolean isPrimary;

    @Column(name = "status", nullable = false)
    private Short status = 1;

    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    @Column(name = "linked_by", length = 100)
    private String linkedBy;
}
