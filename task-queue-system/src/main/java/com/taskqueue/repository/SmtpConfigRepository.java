package com.taskqueue.repository;

import com.taskqueue.model.SmtpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SmtpConfigRepository extends JpaRepository<SmtpConfig, String> {

    // Worker uses this to find which SMTP to use for an email job
    Optional<SmtpConfig> findByCompanyIdAndPurposeAndIsActiveTrue(
            String companyId, SmtpConfig.Purpose purpose
    );

    // Admin UI: list all SMTP configs for a company
    List<SmtpConfig> findByCompanyId(String companyId);
}