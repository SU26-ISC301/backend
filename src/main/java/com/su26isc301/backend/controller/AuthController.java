package com.su26isc301.backend.controller;

import com.su26isc301.backend.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication APIs")
public class AuthController {

    private final JwtService jwtService;

    @GetMapping("/me")
    @Operation(summary = "Get current user info from JWT")
    public ResponseEntity<?> getMe(
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.substring(7);
        String userId = jwtService.extractUserId(token);
        String email = jwtService.extractEmail(token);
        String role = jwtService.extractRole(token);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "email", email,
                "role", role != null ? role : "authenticated"
        ));
    }
}