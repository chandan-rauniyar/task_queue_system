package com.taskqueue.repository;

import com.taskqueue.model.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, String> {

    // ── JOIN FETCH queries — use these everywhere to avoid LazyInit ──

    @Query("""
        SELECT j FROM Job j
        JOIN FETCH j.project p
        JOIN FETCH p.company c
        JOIN FETCH j.apiKey k
        WHERE j.id = :id
    """)
    Optional<Job> findByIdWithRelations(String id);

    @Query("""
        SELECT j FROM Job j
        JOIN FETCH j.project p
        JOIN FETCH p.company c
        JOIN FETCH j.apiKey k
        WHERE j.status = 'FAILED' AND j.retryCount < j.maxRetries
    """)
    List<Job> findRetryableJobs();

    // ── Single project queries ──

    Page<Job> findByProjectId(String projectId, Pageable pageable);

    Page<Job> findByProjectIdAndStatus(String projectId, Job.Status status, Pageable pageable);

    long countByProjectId(String projectId);

    long countByProjectIdAndStatus(String projectId, Job.Status status);

    // ── Multi-project queries (for CLIENT — all projects in their company) ──

    Page<Job> findByProjectIdIn(List<String> projectIds, Pageable pageable);

    Page<Job> findByProjectIdInAndStatus(List<String> projectIds, Job.Status status, Pageable pageable);

    // ── Admin queries ──

    Page<Job> findByStatus(Job.Status status, Pageable pageable);

    long countByStatus(Job.Status status);

    // ── Idempotency ──

    Optional<Job> findByProjectIdAndIdempotencyKey(String projectId, String idempotencyKey);

    // ── Dashboard ──

    @Query("SELECT j.status, COUNT(j) FROM Job j GROUP BY j.status")
    List<Object[]> countGroupedByStatus();
}