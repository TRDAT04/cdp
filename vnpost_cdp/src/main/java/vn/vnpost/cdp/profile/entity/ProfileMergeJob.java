package vn.vnpost.cdp.profile.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.vnpost.cdp.common.entity.BaseEntity;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "profile_merge_jobs")
public class ProfileMergeJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "job_type", nullable = false, length = 100)
    private String jobType;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "success_records")
    private Integer successRecords;

    @Column(name = "conflict_records")
    private Integer conflictRecords;

    @Column(name = "failed_records")
    private Integer failedRecords;

    @Column(name = "status", nullable = false)
    private Short status = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_message")
    private String errorMessage;
}
