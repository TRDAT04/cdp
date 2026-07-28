package vn.vnpost.example.profile.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import vn.vnpost.example.common.entity.BaseAuditFields;

@Getter
@Setter
@Table("profile_merge_rules")
public class ProfileMergeRule extends BaseAuditFields {

    @Id
    @Column("id")
    private Long id;

    @Column("property_name")
    private String propertyName;

    @Column("source_system")
    private String sourceSystem;

    @Column("priority")
    private Integer priority;

    @Column("merge_strategy")
    private String mergeStrategy;

    @Column("allow_overwrite")
    private Boolean allowOverwrite;

    @Column("require_review")
    private Boolean requireReview;

    @Column("description")
    private String description;

    @Column("status")
    private Short status = 1;
}
