package com.taskqueue.dto;

import com.taskqueue.model.Project;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProjectRequest {

    @NotBlank(message = "companyId is required")
    private String companyId;

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must not exceed 150 characters")
    private String name;

    private String description;

    @Builder.Default
    private Project.Environment environment = Project.Environment.PRODUCTION;
}