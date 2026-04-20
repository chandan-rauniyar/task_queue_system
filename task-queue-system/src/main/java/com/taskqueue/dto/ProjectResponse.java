package com.taskqueue.dto;

import com.taskqueue.model.Project;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponse {

    private String id;
    private String name;
    private String description;
    private Project.Environment environment;
    private Boolean isActive;
    private String companyId;
    private String companyName;
    private LocalDateTime createdAt;

    public static ProjectResponse from(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .environment(project.getEnvironment())
                .isActive(project.getIsActive())
                .companyId(project.getCompany().getId())
                .companyName(project.getCompany().getName())
                .createdAt(project.getCreatedAt())
                .build();
    }
}