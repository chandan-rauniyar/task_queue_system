package com.taskqueue.repository;

import com.taskqueue.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {
    List<Project> findByCompanyId(String companyId);
    List<Project> findByCompanyIdAndIsActiveTrue(String companyId);
}