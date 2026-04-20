package com.taskqueue.dto;

import com.taskqueue.model.SmtpConfig;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmtpConfigResponse {

    private String id;
    private String companyId;
    private String companyName;
    private SmtpConfig.Purpose purpose;
    private String label;
    private String fromEmail;
    private String fromName;
    private String host;
    private Integer port;
    private String username;
    // password intentionally excluded — never returned after save
    private Boolean useTls;
    private Boolean isActive;
    private Boolean isVerified;
    private LocalDateTime createdAt;

    public static SmtpConfigResponse from(SmtpConfig config) {
        return SmtpConfigResponse.builder()
                .id(config.getId())
                .companyId(config.getCompany().getId())
                .companyName(config.getCompany().getName())
                .purpose(config.getPurpose())
                .label(config.getLabel())
                .fromEmail(config.getFromEmail())
                .fromName(config.getFromName())
                .host(config.getHost())
                .port(config.getPort())
                .username(config.getUsername())
                .useTls(config.getUseTls())
                .isActive(config.getIsActive())
                .isVerified(config.getIsVerified())
                .createdAt(config.getCreatedAt())
                .build();
    }
}