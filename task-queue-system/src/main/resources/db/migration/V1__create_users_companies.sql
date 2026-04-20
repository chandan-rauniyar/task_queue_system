-- ============================================================
-- V1: Users and Companies
-- Using VARCHAR for enum columns — works perfectly with
-- Hibernate @Enumerated(EnumType.STRING) and PostgreSQL
-- ============================================================

CREATE TABLE users (
                       id              VARCHAR(36)     NOT NULL,
                       email           VARCHAR(255)    NOT NULL,
                       password_hash   VARCHAR(255)    NOT NULL,
                       full_name       VARCHAR(100)    NOT NULL,
                       role            VARCHAR(20)     NOT NULL DEFAULT 'CLIENT',
                       is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
                       last_login_at   TIMESTAMP,
                       created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT pk_users       PRIMARY KEY (id),
                       CONSTRAINT uk_users_email UNIQUE (email),
                       CONSTRAINT ck_users_role  CHECK (role IN ('ADMIN','CLIENT','DEVELOPER','VIEWER'))
);

CREATE TABLE companies (
                           id          VARCHAR(36)     NOT NULL,
                           owner_id    VARCHAR(36)     NOT NULL,
                           name        VARCHAR(150)    NOT NULL,
                           slug        VARCHAR(100)    NOT NULL,
                           is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
                           created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           CONSTRAINT pk_companies       PRIMARY KEY (id),
                           CONSTRAINT uk_companies_slug  UNIQUE (slug),
                           CONSTRAINT fk_companies_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

-- Default admin user  (password = "admin123" bcrypt hashed)
INSERT INTO users (id, email, password_hash, full_name, role, is_active)
VALUES (
           'admin-user-001',
           'admin@taskqueue.local',
           '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewFpx7TZfQHmkbi2',
           'System Admin',
           'ADMIN',
           TRUE
       );