package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.RegisterRequest;
import com.su26isc301.backend.dto.request.LoginRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.AuthResponse;
import com.su26isc301.backend.dto.request.RefreshTokenRequest;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.enums.Roles;
import com.su26isc301.backend.repository.ProfileRepository;
import com.su26isc301.backend.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication APIs")
public class AuthController {

    private final JwtService jwtService;
    private final ProfileRepository profileRepository;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.anon.key}")
    private String supabaseAnonKey;

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản mới")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            // 1. Kiểm tra email hoặc sđt đã tồn tại trong DB của mình chưa (Optional nhưng nên làm)
            if (profileRepository.findByEmail(request.getEmail()).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email đã được sử dụng"));
            }

            // 2. Gọi API của Supabase để tạo User trong auth.users
            RestTemplate restTemplate = new RestTemplate();
            String url = supabaseUrl + "/auth/v1/signup";

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseAnonKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of(
                    "email", request.getEmail(),
                    "password", request.getPassword()
            );
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            // 3. Trích xuất ID (UUID) từ Supabase trả về
//            String supabaseUserId = (String) responseBody.get("id");
            String supabaseUserId = null;

            // Kiểm tra xem ID có nằm trong object "user" không
            if (responseBody.containsKey("user") && responseBody.get("user") instanceof Map) {
                Map<String, Object> userMap = (Map<String, Object>) responseBody.get("user");
                supabaseUserId = (String) userMap.get("id");
            } else {
                supabaseUserId = (String) responseBody.get("id");
            }
            if (supabaseUserId == null) {
                return ResponseEntity.internalServerError().body(
                        Map.of("error", "Không thể lấy ID từ Supabase. Response: " + responseBody)
                );
            }
            // 4. Lưu thông tin Profile vào Database
            Profile newProfile = Profile.builder()
                    .id(UUID.fromString(supabaseUserId))
                    .email(request.getEmail())
                    .phone(request.getPhone())
                    .fullName(request.getFullName())
                    .dateOfBirth(request.getDateOfBirth())
                    .role(Roles.customer)
                    .isActive(true)
                    .build();
            profileRepository.save(newProfile);

            return ResponseEntity.ok(Map.of("message", "Đăng ký thành công", "userId", supabaseUserId));

        } catch (HttpClientErrorException e) {
            // Lỗi từ phía Supabase (ví dụ: pass quá ngắn, email không hợp lệ)
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAs(Map.class));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập bằng Email hoặc Số điện thoại")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            String loginEmail = request.getIdentifier();

            // 1. Kiểm tra xem người dùng nhập Email hay Số điện thoại
            // Nếu chuỗi không chứa ký tự '@', ta coi nó là số điện thoại
            if (!loginEmail.contains("@")) {
                Profile profile = profileRepository.findByPhone(loginEmail)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với số điện thoại này"));

                loginEmail = profile.getEmail();
            }

            RestTemplate restTemplate = new RestTemplate();
            String url = supabaseUrl + "/auth/v1/token?grant_type=password";

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseAnonKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of(
                    "email", loginEmail,
                    "password", request.getPassword()
            );
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            return ResponseEntity.ok(new AuthResponse(
                    (String) responseBody.get("access_token"),
                    (String) responseBody.get("refresh_token"),
                    Long.valueOf(responseBody.get("expires_in").toString())
            ));

        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Tài khoản hoặc mật khẩu không đúng"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user info from JWT")
    public ResponseEntity<?> getMe(
            @RequestHeader("Authorization") String authHeader) {
        try {
        String token = authHeader.substring(7);
        String userId = jwtService.extractUserId(token);
//        String email = jwtService.extractEmail(token);
//        String role = jwtService.extractRole(token);
//
//        return ResponseEntity.ok(Map.of(
//                "userId", userId,
//                "email", email,
//                "role", role != null ? role : "authenticated"
//        ));
            Profile profile = profileRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ người dùng"));

            return ResponseEntity.ok(ApiResponse.success("Lấy thông tin thành công", profile));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Phiên đăng nhập không hợp lệ hoặc đã hết hạn"));
        }
    }


    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            String url = supabaseUrl + "/auth/v1/token?grant_type=refresh_token";

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseAnonKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of("refresh_token", request.getRefreshToken());
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            return ResponseEntity.ok(new AuthResponse(
                    (String) responseBody.get("access_token"),
                    (String) responseBody.get("refresh_token"),
                    Long.valueOf(responseBody.get("expires_in").toString())
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
        }
    }
}