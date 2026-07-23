-- ============================================================
-- V5: User Company Roles
-- Links users to companies with a role (OWNER / DEVELOPER / VIEWER)
-- Added for multi-tenant client login — Phase 5
-- ============================================================

CREATE TABLE user_company_roles (
                                    id          VARCHAR(36)     NOT NULL,
                                    user_id     VARCHAR(36)     NOT NULL,
                                    company_id  VARCHAR(36)     NOT NULL,
                                    role        VARCHAR(20)     NOT NULL DEFAULT 'OWNER',
                                    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT pk_user_company_roles      PRIMARY KEY (id),
                                    CONSTRAINT uk_user_company            UNIQUE (user_id, company_id),
                                    CONSTRAINT fk_ucr_user               FOREIGN KEY (user_id)    REFERENCES users(id),
                                    CONSTRAINT fk_ucr_company            FOREIGN KEY (company_id) REFERENCES companies(id),
                                    CONSTRAINT ck_ucr_role               CHECK (role IN ('OWNER','DEVELOPER','VIEWER'))
);

CREATE INDEX idx_ucr_user    ON user_company_roles(user_id);
CREATE INDEX idx_ucr_company ON user_company_roles(company_id);