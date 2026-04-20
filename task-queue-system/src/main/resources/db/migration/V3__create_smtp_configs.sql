-- ============================================================
-- V3: SMTP Configurations (per company, per purpose)
-- ============================================================

CREATE TABLE smtp_configs (
                              id              VARCHAR(36)     NOT NULL,
                              company_id      VARCHAR(36)     NOT NULL,
                              purpose         VARCHAR(20)     NOT NULL,
                              label           VARCHAR(100)    NOT NULL,
                              from_email      VARCHAR(255)    NOT NULL,
                              from_name       VARCHAR(100)    NOT NULL,
                              host            VARCHAR(255)    NOT NULL,
                              port            INT             NOT NULL DEFAULT 587,
                              username        VARCHAR(255)    NOT NULL,
                              password_enc    TEXT            NOT NULL,
                              use_tls         BOOLEAN         NOT NULL DEFAULT TRUE,
                              is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
                              is_verified     BOOLEAN         NOT NULL DEFAULT FALSE,
                              created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT pk_smtp_configs          PRIMARY KEY (id),
                              CONSTRAINT uk_smtp_company_purpose  UNIQUE (company_id, purpose),
                              CONSTRAINT fk_smtp_company          FOREIGN KEY (company_id) REFERENCES companies(id),
                              CONSTRAINT ck_smtp_purpose          CHECK (purpose IN ('SUPPORT','BILLING','NOREPLY','ALERT','CUSTOM'))
);