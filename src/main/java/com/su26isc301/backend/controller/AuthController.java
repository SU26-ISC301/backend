package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.BuyerProfileUpdateRequest;
import com.su26isc301.backend.dto.request.ForgotPasswordRequest;
import com.su26isc301.backend.dto.request.RegisterRequest;
import com.su26isc301.backend.dto.request.LoginRequest;
import com.su26isc301.backend.dto.request.ProfileUpdateRequest;
import com.su26isc301.backend.dto.request.ResetPasswordRequest;
import com.su26isc301.backend.dto.request.VerifyOtpRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.AuthResponse;
import com.su26isc301.backend.dto.request.RefreshTokenRequest;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.enums.Roles;
import com.su26isc301.backend.repository.ProfileRepository;
import com.su26isc301.backend.service.JwtService;
import com.su26isc301.backend.service.OtpService;
import com.su26isc301.backend.service.SupabaseStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.http.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.time.ZonedDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication APIs")
public class AuthController {

    private final JwtService jwtService;
    private final ProfileRepository profileRepository;
    private final SupabaseStorageService supabaseStorageService;

    private final OtpService otpService;
    private static final long MAX_AVATAR_SIZE_BYTES = 5L * 1024 * 1024;


    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.anon.key}")
    private String supabaseAnonKey;

    @Value("${supabase.service-role.key:}")
    private String supabaseServiceRoleKey;


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
        otpService.checkAndIncrementOtpRateLimit(email);
        otpService.generateAndSendOtp(request);
        return ResponseEntity.ok(Map.of("message", "Vui lòng kiểm tra email để lấy mã OTP xác nhận."));
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("error", "Lỗi khi gửi mail: " + e.getMessage()));
    }
}

    @PostMapping("/register-verify")
    @Operation(summary = "Bước 2: Xác nhận OTP và hoàn tất lưu tài khoản")
    public ResponseEntity<?> verifyRegister(@RequestBody VerifyOtpRequest requestData) {
        try {
            String email = requestData.getEmail();
            String otp = requestData.getOtp();

            if (email == null || otp == null) {
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

    @PostMapping("/password/forgot")
    @Operation(summary = "Bước 1: Gửi OTP quên mật khẩu đến email")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            String email = normalizeEmail(request.getEmail());
            if (email == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email là bắt buộc"));
            }

            profileRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với email này"));

            otpService.checkAndIncrementOtpRateLimit(email);
            otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(ApiResponse.success("Vui lòng kiểm tra email để lấy mã OTP đặt lại mật khẩu.", Map.of("email", email)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Lỗi khi gửi OTP: " + e.getMessage()));
        }
    }

    @PostMapping("/password/verify-otp")
    @Operation(summary = "Bước 2: Xác thực OTP quên mật khẩu")
    public ResponseEntity<?> verifyForgotPasswordOtp(@RequestBody VerifyOtpRequest request) {
        try {
            String email = normalizeEmail(request.getEmail());
            String otp = request.getOtp() == null ? null : request.getOtp().trim();
            if (email == null || otp == null || otp.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email và OTP là bắt buộc"));
            }

            profileRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với email này"));

            if (!otpService.verifyOtp(email, otp)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("OTP không hợp lệ hoặc đã hết hạn"));
            }

            otpService.markEmailVerified(email);
            return ResponseEntity.ok(ApiResponse.success("Xác thực OTP thành công", Map.of("email", email)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Bước 3: Đặt lại mật khẩu sau khi OTP đã xác thực")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            String email = normalizeEmail(request.getEmail());
            if (email == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email là bắt buộc"));
            }
            if (!otpService.isEmailVerified(email)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng xác thực OTP trước khi đặt lại mật khẩu"));
            }
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Mật khẩu mới là bắt buộc"));
            }
            if (request.getPassword().length() < 6) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Mật khẩu mới tối thiểu 6 ký tự"));
            }
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Mật khẩu xác nhận không khớp"));
            }

            Profile profile = profileRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với email này"));

            updateSupabasePassword(profile.getId(), request.getPassword());
            otpService.removeOtp(email);

            return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại."));
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.error("Không thể cập nhật mật khẩu trên Supabase"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    private void updateSupabasePassword(UUID userId, String newPassword) {
        if (supabaseServiceRoleKey == null || supabaseServiceRoleKey.isBlank()) {
            throw new RuntimeException("Backend chưa cấu hình SUPABASE_SERVICE_ROLE_KEY để đặt lại mật khẩu");
        }

        RestTemplate restTemplate = new RestTemplate();
        String url = supabaseUrl + "/auth/v1/admin/users/" + userId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseServiceRoleKey);
        headers.setBearerAuth(supabaseServiceRoleKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("password", newPassword), headers);
        restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
    }

    @PostMapping("/profile/update-otp")
    @Operation(summary = "Gửi OTP xác nhận đổi email hoặc số điện thoại Buyer")
    public ResponseEntity<?> requestProfileUpdateOtp(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody BuyerProfileUpdateRequest request
    ) {
        try {
            Profile profile = getProfileFromAuthHeader(authHeader);
            String nextEmail = normalizeEmail(request.getEmail());
            String nextPhone = normalizePhone(request.getPhone());

            if (nextEmail != null && !nextEmail.equals(profile.getEmail())
                    && profileRepository.findByEmail(nextEmail).isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email đã được sử dụng"));
            }
            if (nextPhone != null && !Objects.equals(nextPhone, profile.getPhone())
                    && profileRepository.findByPhone(nextPhone).isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Số điện thoại đã được sử dụng"));
            }

            boolean emailChanged = nextEmail != null && !nextEmail.equals(profile.getEmail());
            boolean phoneChanged = nextPhone != null && !Objects.equals(nextPhone, profile.getPhone());
            if (!emailChanged && !phoneChanged) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Không có email hoặc số điện thoại mới cần xác thực"));
            }

            String otpTarget = emailChanged ? nextEmail : profile.getEmail();
            otpService.checkAndIncrementOtpRateLimit(otpTarget);
            otpService.generateAndSendOtp(otpTarget);

            return ResponseEntity.ok(ApiResponse.success(
                    emailChanged
                            ? "OTP đã được gửi đến email mới của bạn."
                            : "OTP đã được gửi đến email hiện tại để xác thực đổi số điện thoại.",
                    Map.of("otpTarget", otpTarget)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/profile")
    @Operation(summary = "Cập nhật thông tin cá nhân Buyer")
    public ResponseEntity<?> updateCurrentBuyerProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody BuyerProfileUpdateRequest request
    ) {
        try {
            Profile profile = getProfileFromAuthHeader(authHeader);
            String nextEmail = normalizeEmail(request.getEmail());
            String nextPhone = normalizePhone(request.getPhone());

            if (request.getFullName() == null || request.getFullName().isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Tên người dùng là bắt buộc"));
            }
            if (nextEmail == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email là bắt buộc"));
            }
            if (nextPhone == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Số điện thoại là bắt buộc"));
            }
            if (request.getDateOfBirth() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Ngày sinh là bắt buộc"));
            }

            boolean emailChanged = !nextEmail.equals(profile.getEmail());
            boolean phoneChanged = !Objects.equals(nextPhone, profile.getPhone());
            if (emailChanged && profileRepository.findByEmail(nextEmail).isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email đã được sử dụng"));
            }
            if (phoneChanged && profileRepository.findByPhone(nextPhone).isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Số điện thoại đã được sử dụng"));
            }

            if (emailChanged || phoneChanged) {
                String otpTarget = emailChanged ? nextEmail : profile.getEmail();
                String otp = request.getOtp() == null ? null : request.getOtp().trim();
                if (otp == null || otp.isBlank()) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng nhập OTP xác thực thay đổi"));
                }
                if (!otpService.verifyOtp(otpTarget, otp)) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("OTP không hợp lệ hoặc đã hết hạn"));
                }
                otpService.removeOtp(otpTarget);
            }

            if (emailChanged) {
                updateSupabaseUserEmail(profile.getId(), nextEmail);
            }

            profile.setFullName(request.getFullName().trim());
            profile.setEmail(nextEmail);
            profile.setPhone(nextPhone);
            profile.setDateOfBirth(request.getDateOfBirth());

            return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin thành công", profileRepository.save(profile)));
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.error("Không thể cập nhật email trên Supabase"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload avatar Buyer")
    public ResponseEntity<?> uploadCurrentBuyerAvatar(
            @RequestHeader("Authorization") String authHeader,
            @RequestPart("avatar") MultipartFile avatar
    ) {
//        try {
//            Profile profile = getProfileFromAuthHeader(authHeader);
//            validateAvatar(avatar);
//
//            String extension = getExtension(avatar.getOriginalFilename(), avatar.getContentType());
//            String fileName = profile.getId() + "-" + System.currentTimeMillis() + extension;
//            Path avatarDir = Path.of("uploads", "avatars").toAbsolutePath().normalize();
//            Files.createDirectories(avatarDir);
//            Path destination = avatarDir.resolve(fileName).normalize();
//            avatar.transferTo(destination);
//
//            String avatarUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
//                    .path("/api/auth/uploads/avatars/")
//                    .path(fileName)
//                    .toUriString();
//            profile.setAvatarUrl(avatarUrl);
//
//            return ResponseEntity.ok(ApiResponse.success("Cập nhật ảnh đại diện thành công", profileRepository.save(profile)));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        } catch (IOException e) {
//            return ResponseEntity.internalServerError().body(ApiResponse.error("Không thể lưu ảnh đại diện"));
//        }
        try {
            // 1. Lấy thông tin profile từ token và kiểm tra tính hợp lệ của ảnh
            Profile profile = getProfileFromAuthHeader(authHeader);
            validateAvatar(avatar);

            // [THÊM MỚI 1]: Lấy đường dẫn ảnh cũ (nếu có) trước khi nó bị ghi đè
            String oldAvatarUrl = profile.getAvatarUrl();

            // 2. GỌI SUPABASE SERVICE: Đẩy trực tiếp file mới lên bucket 'avatars'
            String newAvatarUrl = supabaseStorageService.uploadFile(avatar, "avatars");

            // [THÊM MỚI 2]: Gọi hàm dọn rác, truyền link ảnh cũ vào để Supabase xóa đi
            if (oldAvatarUrl != null) {
                supabaseStorageService.deleteFile(oldAvatarUrl, "avatars");
            }

            // 3. Lưu chuỗi URL công khai ảnh MỚI vào trường avatarUrl của Database
            profile.setAvatarUrl(newAvatarUrl);
            Profile savedProfile = profileRepository.save(profile);

            // 4. Trả kết quả thành công về cho Frontend
            return ResponseEntity.ok(ApiResponse.success("Cập nhật ảnh đại diện thành công", savedProfile));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IOException e) {
            // Bổ sung catch lỗi IOException do hàm uploadFile của Supabase ném ra khi xử lý byte array
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Lỗi trong quá trình truyền tải dữ liệu hình ảnh lên hệ thống đám mây"));
        }
    }

    private Profile getProfileFromAuthHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Vui lòng đăng nhập để tiếp tục");
        }
        String token = authHeader.substring(7);
        String userId = jwtService.extractUserId(token);
        return profileRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ người dùng"));
    }

    private void updateSupabaseUserEmail(UUID userId, String newEmail) {
        if (supabaseServiceRoleKey == null || supabaseServiceRoleKey.isBlank()) {
            throw new RuntimeException("Backend chưa cấu hình SUPABASE_SERVICE_ROLE_KEY để cập nhật email");
        }

        RestTemplate restTemplate = new RestTemplate();
        String url = supabaseUrl + "/auth/v1/admin/users/" + userId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseServiceRoleKey);
        headers.setBearerAuth(supabaseServiceRoleKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("email", newEmail, "email_confirm", true),
                headers
        );
        restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
    }

    private void validateAvatar(MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            throw new RuntimeException("Vui lòng chọn ảnh đại diện");
        }
        if (avatar.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new RuntimeException("Ảnh đại diện phải dưới 5MB");
        }
        String contentType = avatar.getContentType();
        if (contentType == null || !(
                contentType.equals("image/jpeg")
                        || contentType.equals("image/png")
                        || contentType.equals("image/webp")
        )) {
            throw new RuntimeException("Ảnh đại diện chỉ hỗ trợ JPG, PNG hoặc WEBP");
        }
    }

    private String getExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
            if (ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png") || ext.equals(".webp")) {
                return ext;
            }
        }
        if ("image/png".equals(contentType)) return ".png";
        if ("image/webp".equals(contentType)) return ".webp";
        return ".jpg";
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

            if (profile.getLockoutUntil() != null && profile.getLockoutUntil().isAfter(ZonedDateTime.now())) {
                long minutesLeft = java.time.Duration.between(ZonedDateTime.now(), profile.getLockoutUntil()).toMinutes() + 1;
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", String.format("Tài khoản đang bị tạm khóa. Vui lòng thử lại sau %d phút.", minutesLeft)));
            }

            if (Boolean.FALSE.equals(profile.getIsActive())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Tài khoản chưa xác thực OTP email"));
            }

            Map<String, Object> responseBody;
            try {
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
                responseBody = response.getBody();
            } catch (HttpClientErrorException e) {
                profile.setFailedLoginAttempts(profile.getFailedLoginAttempts() + 1);
                if (profile.getFailedLoginAttempts() >= 5) {
                    profile.setLockoutUntil(ZonedDateTime.now().plusMinutes(15));
                    profileRepository.save(profile);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Tài khoản đã bị tạm khóa 15 phút do nhập sai mật khẩu quá 5 lần."));
                }
                profileRepository.save(profile);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Tài khoản hoặc mật khẩu không đúng"));
            }

            // Reset failed login attempts on success
            if (profile.getFailedLoginAttempts() > 0 || profile.getLockoutUntil() != null) {
                profile.setFailedLoginAttempts(0);
                profile.setLockoutUntil(null);
            }
            profile.setLastLoginAt(ZonedDateTime.now());
            profileRepository.save(profile);

            return ResponseEntity.ok(new AuthResponse(
                    (String) responseBody.get("access_token"),
                    (String) responseBody.get("refresh_token"),
                    Long.valueOf(responseBody.get("expires_in").toString())
            ));

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

    @PutMapping("/profiles/{profileId}")
    @Operation(summary = "Cập nhật tên và ngày sinh của profile")
    public ResponseEntity<?> updateProfileInfo(
            @PathVariable UUID profileId,
            @RequestBody ProfileUpdateRequest request
    ) {
        try {
            Profile profile = profileRepository.findById(profileId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ người dùng"));

            if (request.getFullName() == null || request.getFullName().isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Tên người dùng là bắt buộc"));
            }
            if (request.getDateOfBirth() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Ngày sinh là bắt buộc"));
            }

            profile.setFullName(request.getFullName().trim());
            profile.setDateOfBirth(request.getDateOfBirth());
            Profile updatedProfile = profileRepository.save(profile);

            return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin thành công", updatedProfile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
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
