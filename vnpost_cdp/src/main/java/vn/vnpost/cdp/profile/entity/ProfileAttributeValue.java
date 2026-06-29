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
@Table(name = "profile_attribute_values")
public class ProfileAttributeValue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "master_profile_id", nullable = false)
    private Long masterProfileId;

    @Column(name = "source_record_id")
    private Long sourceRecordId;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "property_name", length = 200)
    private String propertyName;

    @Column(name = "property_value")
    private String propertyValue;

    @Column(name = "normalized_value")
    private String normalizedValue;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "is_selected")
    private Boolean isSelected;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;
}
