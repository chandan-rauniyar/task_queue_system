package com.taskqueue.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "dead_letter_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterJob {

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    // Reference to the original failed job
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    // Snapshot of the payload at time of final failure
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "original_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> originalPayload;

    // Full error message or stack trace
    @Column(name = "failure_reason", nullable = false, columnDefinition = "TEXT")
    private String failureReason;

    // How many attempts were made before giving up
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "failed_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime failedAt = LocalDateTime.now();

    // NULL = not yet replayed by admin
    @Column(name = "replayed_at")
    private LocalDateTime replayedAt;

    // Points to the new job created when admin replays this
    @Column(name = "replayed_job_id", length = 36)
    private String replayedJobId;

    @Transient
    public boolean isReplayed() {
        return replayedAt != null;
    }
}