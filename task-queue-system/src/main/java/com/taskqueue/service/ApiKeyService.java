package com.taskqueue.service;

import com.taskqueue.dto.CreateApiKeyRequest;
import com.taskqueue.dto.CreateApiKeyResponse;
import com.taskqueue.exception.TaskQueueException;
import com.taskqueue.filter.ApiKeyFilter;
import com.taskqueue.model.ApiKey;
import com.taskqueue.model.Project;
import com.taskqueue.repository.ApiKeyRepository;
import com.taskqueue.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository  apiKeyRepository;
    private final ProjectRepository projectRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Create ────────────────────────────────────────────────

    @Transactional
    public CreateApiKeyResponse createKey(CreateApiKeyRequest req) {
        Project project = projectRepository.findById(req.getProjectId())
                .orElseThrow(() -> TaskQueueException.notFound("Project", req.getProjectId()));

        String env = req.getEnvironment() != null
                ? req.getEnvironment().toLowerCase()
                : "live";

        // Build raw key: prefix + 24 random URL-safe chars
        // e.g. "tq_live_k9x2mA8bQpR3nJ7vXcY1s2p"
        String prefix    = "tq_" + env + "_";
        String randomPart = generateRandom(24);
        String rawKey    = prefix + randomPart;

        // Hash for DB — raw key is NEVER stored
        String keyHash = ApiKeyFilter.sha256(rawKey);

        // Hint: last 4 chars shown in UI e.g. "...s2p"
        String hint = "..." + rawKey.substring(rawKey.length() - 4);

        ApiKey apiKey = ApiKey.builder()
                .project(project)
                .keyPrefix(prefix)
                .keyHash(keyHash)
                .keyHint(hint)
                .label(req.getLabel())
                .rateLimitPerMin(req.getRateLimitPerMin() != null ? req.getRateLimitPerMin() : 100)
                .isActive(true)
                .expiresAt(req.getExpiresAt())
                .build();

        apiKeyRepository.save(apiKey);
        log.info("API key created: id={} project={} label={}",
                apiKey.getId(), req.getProjectId(), req.getLabel());

        return CreateApiKeyResponse.builder()
                .id(apiKey.getId())
                .label(apiKey.getLabel())
                .projectId(project.getId())
                .projectName(project.getName())
                .rawKey(rawKey)          // returned ONCE only
                .keyHint(hint)
                .keyPrefix(prefix)
                .rateLimitPerMin(apiKey.getRateLimitPerMin())
                .expiresAt(apiKey.getExpiresAt())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }

    // ── Revoke ────────────────────────────────────────────────

    @Transactional
    public void revokeKey(String keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> TaskQueueException.notFound("ApiKey", keyId));

        key.setIsActive(false);
        apiKeyRepository.save(key);

        // Evict from Redis immediately so filter stops accepting it
        redisTemplate.delete("apikey:" + key.getKeyHash());
        log.info("API key revoked: id={}", keyId);
    }

    // ── List ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ApiKey> listKeysForProject(String projectId) {
        return apiKeyRepository.findByProjectId(projectId);
    }

    // ── Helper ────────────────────────────────────────────────

    private String generateRandom(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes)
                .substring(0, length);
    }
}