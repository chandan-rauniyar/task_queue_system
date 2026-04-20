package com.taskqueue.repository;

import com.taskqueue.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    /**
     * Used by ApiKeyFilter on every request.
     * JOIN FETCH loads Project and Company in ONE query — no lazy loading needed.
     * This prevents LazyInitializationException outside a transaction (in filters).
     */
    @Query("""
        SELECT k FROM ApiKey k
        JOIN FETCH k.project p
        JOIN FETCH p.company c
        WHERE k.keyHash = :keyHash
    """)
    Optional<ApiKey> findByKeyHashWithProjectAndCompany(String keyHash);

    // Simple lookup — used internally (not in filters)
    Optional<ApiKey> findByKeyHash(String keyHash);

    // List all keys for a project (admin UI)
    List<ApiKey> findByProjectId(String projectId);

    // Update last used timestamp
    @Modifying
    @Transactional
    @Query("UPDATE ApiKey k SET k.lastUsedAt = :now WHERE k.id = :id")
    void updateLastUsedAt(String id, LocalDateTime now);
}