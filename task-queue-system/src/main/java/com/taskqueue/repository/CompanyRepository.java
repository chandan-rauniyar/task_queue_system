package com.taskqueue.repository;

import com.taskqueue.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, String> {
    List<Company> findByOwnerId(String ownerId);
    Optional<Company> findBySlug(String slug);
    boolean existsBySlug(String slug);
}