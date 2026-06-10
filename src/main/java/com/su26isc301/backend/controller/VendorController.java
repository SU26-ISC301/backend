package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.VendorCompleteFormRequest;
import com.su26isc301.backend.dto.request.VendorEmailOtpRequest;
import com.su26isc301.backend.dto.request.LoginRequest;
import com.su26isc301.backend.dto.request.VendorOtpVerifyRequest;
import com.su26isc301.backend.dto.request.VendorOnboardingRequest;
import com.su26isc301.backend.dto.request.VendorRegisterRequest;
import com.su26isc301.backend.dto.request.VendorUpdateRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.MarketResearchResponse;
import com.su26isc301.backend.dto.response.VendorOtpVerifyResponse;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.enums.VendorCategory;
import com.su26isc301.backend.service.MarketResearchService;
import com.su26isc301.backend.service.OtpService;
import com.su26isc301.backend.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;
    private final OtpService otpService;
    private final MarketResearchService marketResearchService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> loginVendor(
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        try {
            String deviceToken = servletRequest.getHeader("X-Device-Token");
            String userAgent = servletRequest.getHeader("User-Agent");
            String ipAddress = servletRequest.getHeader("X-Forwarded-For");
            if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = servletRequest.getRemoteAddr();
            }

            return ResponseEntity.ok(ApiResponse.success(
                    "Đăng nhập Vendor thành công",
                    vendorService.loginVendor(request, deviceToken, userAgent, ipAddress)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/register/start")
    public ResponseEntity<ApiResponse<Map<String, String>>> startVendorRegister(
            @RequestBody VendorEmailOtpRequest request
    ) {
        try {
            String email = normalizeEmail(request.getEmail());
            if (email == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email là bắt buộc"));
            }

            otpService.checkAndIncrementOtpRateLimit(email);
            otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(ApiResponse.success(
                    "Vui lòng kiểm tra email để lấy mã OTP xác nhận.",
                    Map.of("email", email)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/register/verify-otp")
    public ResponseEntity<ApiResponse<VendorOtpVerifyResponse>> verifyVendorRegisterOtp(
            @RequestBody VendorOtpVerifyRequest request
    ) {
        String email = normalizeEmail(request.getEmail());
        String otp = request.getOtp() == null ? null : request.getOtp().trim();
        if (email == null || otp == null || otp.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email và OTP là bắt buộc"));
        }
        if (!otpService.verifyOtp(email, otp)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("OTP không hợp lệ hoặc đã hết hạn"));
        }
        otpService.markEmailVerified(email);

        Optional<Profile> profile = vendorService.findProfileByEmail(email);
        VendorOtpVerifyResponse response = profile
                .map(existingProfile -> VendorOtpVerifyResponse.builder()
                        .existingBuyer(true)
                        .requiresPassword(false)
                        .ownerPhoneLocked(existingProfile.getPhone() != null)
                        .alreadyRegisteredVendor(vendorService.hasVendor(existingProfile))
                        .profileId(existingProfile.getId())
                        .email(existingProfile.getEmail())
                        .ownerPhone(existingProfile.getPhone())
                        .build())
                .orElseGet(() -> VendorOtpVerifyResponse.builder()
                        .existingBuyer(false)
                        .requiresPassword(true)
                        .ownerPhoneLocked(false)
                        .alreadyRegisteredVendor(false)
                        .email(email)
                        .build());

        return ResponseEntity.ok(ApiResponse.success("Xác thực OTP thành công", response));
    }

    @PostMapping(value = "/register/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Vendor>> completeVendorRegister(
            @ModelAttribute VendorCompleteFormRequest form
    ) {
        try {
            VendorOnboardingRequest request = toOnboardingRequest(form);
            String email = normalizeEmail(request.getEmail());
            if (email == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email là bắt buộc"));
            }
            if (!otpService.isEmailVerified(email)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email chưa được xác thực OTP hoặc phiên xác thực đã hết hạn"));
            }

            request.setEmail(email);
            Vendor newVendor = vendorService.registerVendorOnboarding(request);
            otpService.removeOtp(email);
            return new ResponseEntity<>(
                    ApiResponse.success("Đăng ký Seller thành công!", newVendor),
                    HttpStatus.CREATED
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // 1. Đăng ký mở cửa hàng mới
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Vendor>> registerVendor(@RequestBody VendorRegisterRequest request) {
        Vendor newVendor = vendorService.registerVendor(request);
        ApiResponse<Vendor> response = ApiResponse.<Vendor>builder()
                .success(true)
                .message("Đăng ký thông tin cửa hàng thành công!")
                .data(newVendor)
                .build();
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. Cập nhật thông tin cửa hàng
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Vendor>> updateVendor(
            @PathVariable("id") Long vendorId,
            @RequestBody VendorUpdateRequest request) {
        Vendor updatedVendor = vendorService.updateVendor(vendorId, request);
        ApiResponse<Vendor> response = ApiResponse.<Vendor>builder()
                .success(true)
                .message("Cập nhật thông tin cửa hàng thành công!")
                .data(updatedVendor)
                .build();
        return ResponseEntity.ok(response);
    }

    // 3. Lấy thông tin chi tiết một cửa hàng qua Vendor ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Vendor>> getVendorById(@PathVariable("id") Long vendorId) {
        Vendor vendor = vendorService.getVendorById(vendorId);
        ApiResponse<Vendor> response = ApiResponse.<Vendor>builder()
                .success(true)
                .message("Lấy thông tin cửa hàng thành công")
                .data(vendor)
                .build();
        return ResponseEntity.ok(response);
    }

    // 4. Lấy thông tin cửa hàng qua Profile ID (Dùng khi user đăng nhập muốn xem gian hàng của họ)
    @GetMapping("/profile/{profileId}")
    public ResponseEntity<ApiResponse<Vendor>> getVendorByProfileId(@PathVariable("profileId") UUID profileId) {
        Vendor vendor = vendorService.getVendorByProfileId(profileId);
        ApiResponse<Vendor> response = ApiResponse.<Vendor>builder()
                .success(true)
                .message("Lấy thông tin cửa hàng thành công")
                .data(vendor)
                .build();
        return ResponseEntity.ok(response);
    }

    // 5. Danh sách toàn bộ các cửa hàng (Dành cho Admin quản lý hoặc trang tổng hợp)
    @GetMapping
    public ResponseEntity<ApiResponse<List<Vendor>>> getAllVendors() {
        List<Vendor> vendors = vendorService.getAllVendors();
        ApiResponse<List<Vendor>> response = ApiResponse.<List<Vendor>>builder()
                .success(true)
                .message("Lấy danh sách cửa hàng thành công")
                .data(vendors)
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/market-research")
    public ResponseEntity<ApiResponse<MarketResearchResponse>> getVendorMarketResearch(
            Authentication authentication,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "query", required = false) String query
    ) {
        String email = authentication == null ? null : String.valueOf(authentication.getPrincipal());
        MarketResearchResponse response = marketResearchService.getVendorMarketResearch(email, categoryId, source, query);
        return ResponseEntity.ok(ApiResponse.success("Lấy dữ liệu nghiên cứu thị trường thành công", response));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private VendorOnboardingRequest toOnboardingRequest(VendorCompleteFormRequest form) {
        VendorOnboardingRequest request = new VendorOnboardingRequest();
        request.setEmail(form.getEmail());
        request.setPassword(form.getPassword());
        request.setConfirmPassword(form.getConfirmPassword());
        request.setOwnerPhone(form.getOwnerPhone());
        request.setShopName(form.getShopName());
        request.setCategory(VendorCategory.fromValue(form.getCategory()));
        request.setShopEmail(form.getShopEmail());
        request.setShopPhone(form.getShopPhone());
        request.setCccd(form.getCccd());
        request.setTaxCode(form.getTaxCode());
        request.setOwnerFullName(form.getOwnerFullName());
        request.setOwnerDateOfBirth(form.getOwnerDateOfBirth());
        return request;
    }
}
