-- ============================================================
-- V4: Jobs and Dead Letter Queue
-- ============================================================

CREATE TABLE jobs (
                      id                  VARCHAR(36)     NOT NULL,
                      project_id          VARCHAR(36)     NOT NULL,
                      api_key_id          VARCHAR(36)     NOT NULL,
                      type                VARCHAR(100)    NOT NULL,
                      payload             JSONB           NOT NULL,
                      status              VARCHAR(20)     NOT NULL DEFAULT 'QUEUED',
                      priority            VARCHAR(10)     NOT NULL DEFAULT 'NORMAL',
                      retry_count         INT             NOT NULL DEFAULT 0,
                      max_retries         INT             NOT NULL DEFAULT 3,
                      callback_url        VARCHAR(500),
                      idempotency_key     VARCHAR(255),
                      scheduled_at        TIMESTAMP,
                      started_at          TIMESTAMP,
                      completed_at        TIMESTAMP,
                      error_message       TEXT,
                      created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

                      CONSTRAINT pk_jobs              PRIMARY KEY (id),
                      CONSTRAINT uk_jobs_idempotency  UNIQUE (project_id, idempotency_key),
                      CONSTRAINT fk_jobs_project      FOREIGN KEY (project_id) REFERENCES projects(id),
                      CONSTRAINT fk_jobs_api_key      FOREIGN KEY (api_key_id) REFERENCES api_keys(id),
                      CONSTRAINT ck_jobs_status       CHECK (status   IN ('QUEUED','RUNNING','SUCCESS','FAILED','DEAD')),
                      CONSTRAINT ck_jobs_priority     CHECK (priority IN ('HIGH','NORMAL','LOW'))
);

CREATE INDEX idx_jobs_project_status ON jobs(project_id, status);
CREATE INDEX idx_jobs_status         ON jobs(status);
CREATE INDEX idx_jobs_api_key        ON jobs(api_key_id);
CREATE INDEX idx_jobs_created_at     ON jobs(created_at DESC);

CREATE TABLE dead_letter_jobs (
                                  id                  VARCHAR(36)     NOT NULL,
                                  job_id              VARCHAR(36)     NOT NULL,
                                  original_payload    JSONB           NOT NULL,
                                  failure_reason      TEXT            NOT NULL,
                                  retry_count         INT             NOT NULL,
                                  failed_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  replayed_at         TIMESTAMP,
                                  replayed_job_id     VARCHAR(36),

                                  CONSTRAINT pk_dead_letter_jobs  PRIMARY KEY (id),
                                  CONSTRAINT fk_dlq_job           FOREIGN KEY (job_id) REFERENCES jobs(id)
);

CREATE INDEX idx_dlq_job      ON dead_letter_jobs(job_id);
CREATE INDEX idx_dlq_replayed ON dead_letter_jobs(replayed_at);