package com.taskqueue.dto;

import com.taskqueue.model.Company;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyResponse {

    private String id;
    private String name;
    private String slug;
    private Boolean isActive;
    private String ownerEmail;
    private LocalDateTime createdAt;

    public static CompanyResponse from(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .slug(company.getSlug())
                .isActive(company.getIsActive())
                .ownerEmail(company.getOwner().getEmail())
                .createdAt(company.getCreatedAt())
                .build();
    }
}