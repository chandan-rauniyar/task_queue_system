package com.taskqueue.controller;

import com.taskqueue.dto.ApiResponse;
import com.taskqueue.exception.TaskQueueException;
import com.taskqueue.model.User;
import com.taskqueue.repository.UserRepository;
import com.taskqueue.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository  userRepository;
    private final JwtService      jwtService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, String>>> login(
        @RequestBody Map<String, String> body
    ) {
        String email    = body.get("email");
        String password = body.get("password");

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> TaskQueueException.badRequest("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw TaskQueueException.badRequest("Invalid credentials");
        }

        if (!user.getIsActive()) {
            throw TaskQueueException.forbidden("Account is disabled");
        }

        String token = jwtService.generateToken(
            user.getId().toString(), user.getEmail(), user.getRole().name()
        );

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "token", token,
            "email", user.getEmail(),
            "role",  user.getRole().name()
        )));
    }
}
