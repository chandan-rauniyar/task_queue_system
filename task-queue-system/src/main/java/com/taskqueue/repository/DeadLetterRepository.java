package com.taskqueue.repository;

import com.taskqueue.model.DeadLetterJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterJob, String> {

    // Admin DLQ page: show unreplayed dead jobs first
    Page<DeadLetterJob> findByReplayedAtIsNull(Pageable pageable);

    Optional<DeadLetterJob> findByJobId(String jobId);

    long countByReplayedAtIsNull();
}