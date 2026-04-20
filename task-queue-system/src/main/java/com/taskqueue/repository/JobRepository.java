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

    /**
     * Load a single job with ALL lazy relations eagerly.
     * Use this everywhere you need job.getProject() or job.getApiKey().
     * Prevents LazyInitializationException in workers, services, filters.
     */
    @Query("""
        SELECT j FROM Job j
        JOIN FETCH j.project p
        JOIN FETCH p.company c
        JOIN FETCH j.apiKey k
        WHERE j.id = :id
    """)
    Optional<Job> findByIdWithRelations(String id);

    // Paginated list for client API — project jobs
    Page<Job> findByProjectId(String projectId, Pageable pageable);

    // Filter by project + status
    Page<Job> findByProjectIdAndStatus(String projectId, Job.Status status, Pageable pageable);

    // Admin browser — all jobs by status
    Page<Job> findByStatus(Job.Status status, Pageable pageable);

    // Count by status for metrics dashboard
    long countByStatus(Job.Status status);

    // Idempotency check
    Optional<Job> findByProjectIdAndIdempotencyKey(String projectId, String idempotencyKey);

    // RetryService — find jobs eligible for retry
    @Query("""
        SELECT j FROM Job j
        JOIN FETCH j.project p
        JOIN FETCH p.company c
        JOIN FETCH j.apiKey k
        WHERE j.status = 'FAILED'
        AND j.retryCount < j.maxRetries
    """)
    List<Job> findRetryableJobs();

    // Dashboard status breakdown
    @Query("SELECT j.status, COUNT(j) FROM Job j GROUP BY j.status")
    List<Object[]> countGroupedByStatus();
}