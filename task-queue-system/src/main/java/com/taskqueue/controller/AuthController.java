package com.taskqueue.controller;

import com.taskqueue.dto.ApiResponse;
import com.taskqueue.exception.TaskQueueException;
import com.taskqueue.model.Company;
import com.taskqueue.model.User;
import com.taskqueue.repository.CompanyRepository;
import com.taskqueue.repository.UserRepository;
import com.taskqueue.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * POST /auth/register  — name + email + password + companyName → JWT
 * POST /auth/login     — email + password → JWT
 *
 * No API key or JWT needed on these endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Register and login")
public class AuthController {

    private final UserRepository    userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder   passwordEncoder;
    private final JwtService        jwtService;

    // ── Register ──────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register — creates user + company in one step, returns JWT")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(
            @Valid @RequestBody RegisterRequest req
    ) {
        // 1. Email must be unique
        if (userRepository.existsByEmail(req.getEmail())) {
            throw TaskQueueException.conflict(
                    "An account with email '" + req.getEmail() + "' already exists"
            );
        }

        // 2. Create user
        User user = User.builder()
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .role(User.Role.CLIENT)
                .isActive(true)
                .build();
        user = userRepository.save(user);
        log.info("User registered: email={}", user.getEmail());

        // 3. Create company with auto-generated unique slug
        String slug = toSlug(req.getCompanyName());
        slug = uniqueSlug(slug);

        Company company = Company.builder()
                .owner(user)
                .name(req.getCompanyName())
                .slug(slug)
                .isActive(true)
                .build();
        company = companyRepository.save(company);
        log.info("Company created: name={} slug={}", company.getName(), company.getSlug());

        // 4. JWT carries companyId so client can use /client/** immediately
        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getFullName(),
                company.getId()
        );

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "token",       token,
                "email",       user.getEmail(),
                "name",        user.getFullName(),
                "role",        user.getRole().name(),
                "companyId",   company.getId(),
                "companyName", company.getName()
        )));
    }

    // ── Login ─────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Login — returns JWT")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @Valid @RequestBody LoginRequest req
    ) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> TaskQueueException.badRequest("Invalid email or password"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw TaskQueueException.badRequest("Invalid email or password");
        }

        if (!user.getIsActive()) {
            throw TaskQueueException.forbidden("Account deactivated. Contact admin.");
        }

        // Find company for CLIENT users
        String companyId   = null;
        String companyName = null;
        if (user.getRole() == User.Role.CLIENT) {
            var companies = companyRepository.findByOwnerId(user.getId());
            if (!companies.isEmpty()) {
                companyId   = companies.get(0).getId();
                companyName = companies.get(0).getName();
            }
        }

        String token = jwtService.generateToken(
                user.getId(), user.getEmail(),
                user.getRole().name(), user.getFullName(),
                companyId
        );

        log.info("Login: email={} role={}", user.getEmail(), user.getRole());

        var map = new HashMap<String, Object>();
        map.put("token", token);
        map.put("email", user.getEmail());
        map.put("name",  user.getFullName());
        map.put("role",  user.getRole().name());
        if (companyId   != null) map.put("companyId",   companyId);
        if (companyName != null) map.put("companyName", companyName);

        return ResponseEntity.ok(ApiResponse.ok(map));
    }

    // ── DTOs ──────────────────────────────────────────────────

    @Data
    public static class RegisterRequest {

        @NotBlank(message = "Full name is required")
        @Size(max = 100)
        private String fullName;

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        @NotBlank(message = "Company name is required")
        @Size(max = 150)
        private String companyName;
    }

    @Data
    public static class LoginRequest {
        @NotBlank @Email
        private String email;
        @NotBlank
        private String password;
    }

    // ── Helpers ───────────────────────────────────────────────

    private String toSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private String uniqueSlug(String base) {
        if (!companyRepository.existsBySlug(base)) return base;
        int i = 2;
        while (companyRepository.existsBySlug(base + "-" + i)) i++;
        return base + "-" + i;
    }
}