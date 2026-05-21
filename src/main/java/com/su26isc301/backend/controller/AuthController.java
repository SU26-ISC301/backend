package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.RefreshTokenRequest;
import com.su26isc301.backend.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

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

//    @PostMapping("/refresh")
//    @Operation(summary = "Refresh access token")
//    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
//        try {
//            RestTemplate restTemplate = new RestTemplate();
//
//            String url = supabaseUrl + "/auth/v1/token?grant_type=refresh_token";
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.set("apikey", supabaseAnonKey);
//            headers.setContentType(MediaType.APPLICATION_JSON);
//
//            Map<String, String> body = Map.of("refresh_token", request.getRefreshToken());
//            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
//
//            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
//            Map<String, Object> responseBody = response.getBody();
//
//            return ResponseEntity.ok(new AuthResponse(
//                    (String) responseBody.get("access_token"),
//                    (String) responseBody.get("refresh_token"),
//                    Long.valueOf(responseBody.get("expires_in").toString())
//            ));
//        } catch (Exception e) {
//            return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
//        }
//    }
}