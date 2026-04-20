package com.taskqueue.dto;

import com.taskqueue.model.SmtpConfig;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSmtpRequest {

    @NotBlank(message = "companyId is required")
    private String companyId;

    @NotNull(message = "purpose is required")
    private SmtpConfig.Purpose purpose;

    @NotBlank(message = "label is required")
    @Size(max = 100)
    private String label;

    // The FROM address e.g. support@swiggy.com
    @NotBlank(message = "fromEmail is required")
    @Email(message = "fromEmail must be a valid email address")
    private String fromEmail;

    // The FROM name shown to recipient e.g. "Swiggy Support"
    @NotBlank(message = "fromName is required")
    private String fromName;

    // SMTP server hostname e.g. smtp.gmail.com
    @NotBlank(message = "host is required")
    private String host;

    @Builder.Default
    private Integer port = 587;

    @NotBlank(message = "username is required")
    private String username;

    // Raw password — encrypted by EncryptionService before saving to DB
    @NotBlank(message = "password is required")
    private String password;

    @Builder.Default
    private Boolean useTls = true;
}