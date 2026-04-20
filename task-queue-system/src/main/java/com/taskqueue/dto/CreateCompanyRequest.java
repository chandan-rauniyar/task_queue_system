package com.taskqueue.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCompanyRequest {

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must not exceed 150 characters")
    private String name;

    @NotBlank(message = "slug is required")
    @Size(max = 100, message = "slug must not exceed 100 characters")
    @Pattern(
            regexp = "^[a-z0-9-]+$",
            message = "slug must contain only lowercase letters, numbers, and hyphens"
    )
    private String slug;
}