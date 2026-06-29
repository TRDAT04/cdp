package vn.vnpost.cdp.profile.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.vnpost.cdp.common.entity.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "profile_source_systems")
public class ProfileSourceSystem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "source_type", length = 100)
    private String sourceType;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "status", nullable = false)
    private Short status = 1;
}
