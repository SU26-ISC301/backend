package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.RegisterRequest;
import com.su26isc301.backend.dto.request.LoginRequest;
import com.su26isc301.backend.dto.request.VerifyOtpRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.AuthResponse;
import com.su26isc301.backend.dto.request.RefreshTokenRequest;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.enums.Roles;
import com.su26isc301.backend.repository.ProfileRepository;
import com.su26isc301.backend.service.JwtService;
import com.su26isc301.backend.service.OtpService;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication APIs")
public class AuthController {

    private final JwtService jwtService;
    private final ProfileRepository profileRepository;

    private final OtpService otpService;


    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.anon.key}")
    private String supabaseAnonKey;


@PostMapping("/register")
@Operation(summary = "Bước 1: Yêu cầu đăng ký (Hệ thống sẽ gửi OTP vào email)")
public ResponseEntity<?> requestRegister(@RequestBody RegisterRequest request) {
    try {
        String email = normalizeEmail(request.getEmail());
        String phone = normalizePhone(request.getPhone());
        if (email == null || request.getPassword() == null || request.getPassword().isBlank()
                || request.getFullName() == null || request.getFullName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email, mật khẩu và họ tên là bắt buộc"));
        }

        request.setEmail(email);
        request.setPhone(phone);

        if (profileRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email đã được sử dụng"));
        }
        if (phone != null && profileRepository.findByPhone(phone).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Số điện thoại đã được sử dụng"));
        }
        otpService.generateAndSendOtp(request);
        return ResponseEntity.ok(Map.of("message", "Vui lòng kiểm tra email để lấy mã OTP xác nhận."));
    } catch (Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("error", "Lỗi khi gửi mail: " + e.getMessage()));
    }
}

    @PostMapping("/register-verify")
    @Operation(summary = "Bước 2: Xác nhận OTP và hoàn tất lưu tài khoản")
    public ResponseEntity<?> verifyRegister(@RequestBody VerifyOtpRequest requestData) {
        try {
            String email = normalizeEmail(requestData.getEmail());
            String otp = requestData.getOtp() == null ? null : requestData.getOtp().trim();

            if (email == null || otp == null || otp.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email và OTP là bắt buộc"));
            }

            // 1. Xác thực OTP và lấy lại thông tin user đã lưu tạm trên RAM
            RegisterRequest pendingUser = otpService.verifyAndGetPayload(email, otp);
            if (pendingUser == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "OTP không hợp lệ hoặc đã hết hạn"));
            }

            // 2. Double check DB (đề phòng ai đó đã đăng ký email này trong lúc user chờ nhập OTP)
            if (profileRepository.findByEmail(pendingUser.getEmail()).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email đã được sử dụng"));
            }
            if (pendingUser.getPhone() != null && profileRepository.findByPhone(pendingUser.getPhone()).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Số điện thoại đã được sử dụng"));
            }

            // 3. Gọi API của Supabase để tạo User (Giờ mới thực sự tạo user)
            RestTemplate restTemplate = new RestTemplate();
            String url = supabaseUrl + "/auth/v1/signup";
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseAnonKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of(
                    "email", pendingUser.getEmail(),
                    "password", pendingUser.getPassword()
            );
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            // 4. Trích xuất ID (UUID) từ Supabase
            String supabaseUserId = null;
            if (responseBody.containsKey("user") && responseBody.get("user") instanceof Map) {
                Map<String, Object> userMap = (Map<String, Object>) responseBody.get("user");
                supabaseUserId = (String) userMap.get("id");
            } else {
                supabaseUserId = (String) responseBody.get("id");
            }

            if (supabaseUserId == null) {
                return ResponseEntity.internalServerError().body(
                        Map.of("error", "Không thể lấy ID từ Supabase.")
                );
            }

            // 5. Lưu thông tin Profile vào Database
            Profile newProfile = Profile.builder()
                    .id(UUID.fromString(supabaseUserId))
                    .email(pendingUser.getEmail())
                    .phone(pendingUser.getPhone())
                    .fullName(pendingUser.getFullName())
                    .dateOfBirth(pendingUser.getDateOfBirth())
                    .role(Roles.customer)
                    .isActive(true)
                    .build();
            profileRepository.save(newProfile);
            otpService.removeOtp(email);

            return ResponseEntity.ok(Map.of(
                    "message", "Đăng ký và xác thực thành công. Bạn có thể đăng nhập ngay.",
                    "userId", supabaseUserId
            ));
        } catch (HttpClientErrorException e) {
            return handleSupabaseRegisterError(e);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }

    private ResponseEntity<?> handleSupabaseRegisterError(HttpClientErrorException e) {
        Map<String, Object> errorBody = e.getResponseBodyAs(Map.class);
        Object code = errorBody == null ? null : errorBody.get("code");
        Object message = errorBody == null ? null : errorBody.get("msg");
        if (message == null && errorBody != null) {
            message = errorBody.get("message");
        }

        if ("user_already_exists".equals(code) || "user_already_exist".equals(code)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Email đã tồn tại trong Supabase Auth nhưng chưa chắc đã có trong bảng profiles. Hãy xóa user cũ trong Supabase Auth hoặc đồng bộ lại profile.",
                    "code", code
            ));
        }

        return ResponseEntity.status(e.getStatusCode()).body(
                errorBody != null ? errorBody : Map.of("error", message != null ? message : e.getMessage())
        );
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập bằng Email hoặc Số điện thoại")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            String loginEmail = request.getIdentifier();

            // 1. Kiểm tra xem người dùng nhập Email hay Số điện thoại
            if (!loginEmail.contains("@")) {
                Profile profile = profileRepository.findByPhone(loginEmail)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với số điện thoại này"));
                loginEmail = profile.getEmail();
            }
            Profile profile = profileRepository.findByEmail(loginEmail)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));
            if (Boolean.FALSE.equals(profile.getIsActive())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Tài khoản chưa xác thực OTP email"));
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
