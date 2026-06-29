package vn.vnpost.cdp.profile.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.vnpost.cdp.common.entity.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "profile_merge_rules")
public class ProfileMergeRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "property_name", nullable = false, length = 200)
    private String propertyName;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "merge_strategy", length = 100)
    private String mergeStrategy;

    @Column(name = "allow_overwrite")
    private Boolean allowOverwrite;

    @Column(name = "require_review")
    private Boolean requireReview;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false)
    private Short status = 1;
}
