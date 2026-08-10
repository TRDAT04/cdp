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
@Table("profile_attribute_values")
public class ProfileAttributeValue extends BaseAuditFields {

    @Id
    @Column("id")
    private Long id;

    @Column("master_profile_id")
    private Long masterProfileId;

    @Column("source_record_id")
    private Long sourceRecordId;

    @Column("source_system")
    private String sourceSystem;

    @Column("property_name")
    private String propertyName;

    @Column("property_value")
    private String propertyValue;

    @Column("normalized_value")
    private String normalizedValue;

    @Column("confidence_score")
    private BigDecimal confidenceScore;

    @Column("is_selected")
    private Boolean isSelected;

    @Column("received_at")
    private LocalDateTime receivedAt;
}
