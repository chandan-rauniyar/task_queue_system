package com.taskqueue.repository;

import com.taskqueue.model.DeadLetterJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterJob, String> {

    // ── Admin — all unreplayed, JOIN FETCH to avoid LazyInit ──
    @Query("""
        SELECT d FROM DeadLetterJob d
        JOIN FETCH d.job j
        JOIN FETCH j.project p
        JOIN FETCH p.company c
        WHERE d.replayedAt IS NULL
    """)
    List<DeadLetterJob> findAllPendingWithRelations();

    // COUNT for metrics (no fetch needed)
    long countByReplayedAtIsNull();

    // Single entry with relations for replay
    @Query("""
        SELECT d FROM DeadLetterJob d
        JOIN FETCH d.job j
        JOIN FETCH j.project p
        JOIN FETCH p.company c
        WHERE d.id = :id
    """)
    Optional<DeadLetterJob> findByIdWithRelations(String id);

    // ── CLIENT — scoped to their company's projects ──
    @Query("""
        SELECT d FROM DeadLetterJob d
        JOIN FETCH d.job j
        JOIN FETCH j.project p
        JOIN FETCH p.company c
        WHERE p.id IN :projectIds
        AND d.replayedAt IS NULL
    """)
    Page<DeadLetterJob> findByJobProjectIdInAndReplayedAtIsNull(
            List<String> projectIds, Pageable pageable
    );

    @Query("""
        SELECT COUNT(d) FROM DeadLetterJob d
        WHERE d.job.project.id IN :projectIds
        AND d.replayedAt IS NULL
    """)
    long countByJobProjectIdInAndReplayedAtIsNull(List<String> projectIds);
}