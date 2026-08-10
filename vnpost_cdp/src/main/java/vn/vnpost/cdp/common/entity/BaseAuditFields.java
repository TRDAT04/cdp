package vn.vnpost.cdp.common.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

@Getter
@Setter
public abstract class BaseAuditFields {

    @Column("created_by")
    private String createdBy;

    @Column("created")
    private LocalDateTime created;

    @Column("modified")
    private LocalDateTime modified;

    @Column("modified_by")
    private String modifiedBy;
}
