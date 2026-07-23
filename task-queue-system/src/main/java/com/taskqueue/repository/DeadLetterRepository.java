package com.taskqueue.repository;

import com.taskqueue.model.DeadLetterJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterJob, String> {

    // Admin — all unreplayed
    Page<DeadLetterJob> findByReplayedAtIsNull(Pageable pageable);

    long countByReplayedAtIsNull();

    // CLIENT — only their company's unreplayed DLQ entries
    @Query("""
        SELECT d FROM DeadLetterJob d
        WHERE d.job.project.id IN :projectIds
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