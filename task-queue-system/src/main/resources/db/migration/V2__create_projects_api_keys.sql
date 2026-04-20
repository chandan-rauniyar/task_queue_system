-- ============================================================
-- V2: Projects and API Keys
-- ============================================================

CREATE TABLE projects (
                          id              VARCHAR(36)     NOT NULL,
                          company_id      VARCHAR(36)     NOT NULL,
                          name            VARCHAR(150)    NOT NULL,
                          description     TEXT,
                          environment     VARCHAR(20)     NOT NULL DEFAULT 'PRODUCTION',
                          is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
                          created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT pk_projects          PRIMARY KEY (id),
                          CONSTRAINT fk_projects_company  FOREIGN KEY (company_id) REFERENCES companies(id),
                          CONSTRAINT ck_projects_env      CHECK (environment IN ('PRODUCTION','STAGING','DEV'))
);

CREATE TABLE api_keys (
                          id                  VARCHAR(36)     NOT NULL,
                          project_id          VARCHAR(36)     NOT NULL,
                          key_prefix          VARCHAR(16)     NOT NULL,
                          key_hash            VARCHAR(255)    NOT NULL,
                          key_hint            VARCHAR(20)     NOT NULL,
                          label               VARCHAR(100)    NOT NULL,
                          rate_limit_per_min  INT             NOT NULL DEFAULT 100,
                          is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
                          expires_at          TIMESTAMP,
                          last_used_at        TIMESTAMP,
                          created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT pk_api_keys          PRIMARY KEY (id),
                          CONSTRAINT uk_api_keys_hash     UNIQUE (key_hash),
                          CONSTRAINT fk_api_keys_project  FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE INDEX idx_api_keys_project ON api_keys(project_id);