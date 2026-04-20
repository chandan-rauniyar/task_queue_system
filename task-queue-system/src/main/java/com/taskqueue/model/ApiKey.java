package com.taskqueue.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_keys")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // Visible prefix e.g. "tq_live_" — not secret
    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    // SHA-256 of the full raw key — NEVER store raw key
    @Column(name = "key_hash", nullable = false, unique = true, length = 255)
    private String keyHash;

    // Last 4 chars shown in admin UI e.g. "...x2m9"
    @Column(name = "key_hint", nullable = false, length = 20)
    private String keyHint;

    // Human label e.g. "Production Key Jan 2025"
    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "rate_limit_per_min", nullable = false)
    @Builder.Default
    private Integer rateLimitPerMin = 100;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // NULL = never expires
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // Checks active + not expired — called by ApiKeyFilter
    @Transient
    public boolean isValid() {
        if (Boolean.FALSE.equals(isActive)) return false;
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) return false;
        return true;
    }
}