package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.request.VendorRegisterRequest;
import com.su26isc301.backend.dto.request.LoginRequest;
import com.su26isc301.backend.dto.request.VendorOnboardingRequest;
import com.su26isc301.backend.dto.request.VendorUpdateRequest;
import com.su26isc301.backend.dto.response.VendorLoginResponse;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.enums.Roles;
import com.su26isc301.backend.enums.VendorCategory;
import com.su26isc301.backend.repository.ProfileRepository;
import com.su26isc301.backend.repository.VendorRepository;
import com.su26isc301.backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VendorService {

    private final VendorRepository vendorRepository;
    private final ProfileRepository profileRepository;
    private final SubscriptionService subscriptionService;
    private final OtpService otpService;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.anon.key}")
    private String supabaseAnonKey;

    @Transactional
    public Vendor registerVendor(VendorRegisterRequest request) {
        Profile profile = profileRepository.findById(request.getProfileId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin tài khoản"));

        if (vendorRepository.findByProfile(profile).isPresent()) {
            throw new RuntimeException("Tài khoản này đã đăng ký cửa hàng kinh doanh trước đó!");
        }
        profile.setRole(Roles.vendor);
        profileRepository.save(profile);

        Vendor vendor = Vendor.builder()
                .profile(profile)
                .shopName(request.getShopName())
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .email(request.getEmail())
                .phone(request.getPhone())
                .category(categoryValue(request.getCategory()))
                .cccd(request.getCccd())
                .taxCode(request.getTaxCode())
                .cccdFrontImageUrl(request.getCccdFrontImageUrl())
                .cccdBackImageUrl(request.getCccdBackImageUrl())
                .faceImageUrl(request.getFaceImageUrl())
                .build();

        Vendor saved = vendorRepository.save(vendor);
        subscriptionService.getOrCreateFreePlan(saved.getId());
        return saved;
    }

    @Transactional
    public Vendor registerVendorOnboarding(VendorOnboardingRequest request) {
        String email = normalizeEmail(request.getEmail());
        String ownerPhone = normalizePhone(request.getOwnerPhone());
        validateOnboardingRequest(request, email);
        LocalDate ownerDateOfBirth = parseDateOfBirth(request.getOwnerDateOfBirth());

        Profile profile = profileRepository.findByEmail(email)
                .orElseGet(() -> createProfileForNewVendor(request, email, ownerPhone, ownerDateOfBirth));

        if (vendorRepository.findByProfile(profile).isPresent()) {
            throw new RuntimeException("Tài khoản này đã đăng ký cửa hàng kinh doanh trước đó!");
        }

        if (profile.getPhone() != null && ownerPhone != null && !profile.getPhone().equals(ownerPhone)) {
            throw new RuntimeException("Số điện thoại chủ shop không khớp với tài khoản Buyer hiện có");
        }
        if (profile.getPhone() == null && ownerPhone != null) {
            profile.setPhone(ownerPhone);
        }
        profile.setFullName(blankToNull(request.getOwnerFullName()));
        profile.setDateOfBirth(ownerDateOfBirth);
        profile.setRole(Roles.vendor);
        profileRepository.save(profile);

        Vendor vendor = Vendor.builder()
                .profile(profile)
                .shopName(request.getShopName().trim())
                .description(null)
                .logoUrl(null)
                .email(normalizeEmail(request.getShopEmail()))
                .phone(normalizePhone(request.getShopPhone()))
                .category(categoryValue(request.getCategory()))
                .cccd(blankToNull(request.getCccd()))
                .taxCode(blankToNull(request.getTaxCode()))
                .build();

        Vendor saved = vendorRepository.save(vendor);
        subscriptionService.getOrCreateFreePlan(saved.getId());
        return saved;
    }

    @Transactional
    public Vendor updateVendor(Long vendorId, VendorUpdateRequest request) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng với ID: " + vendorId));

        vendor.setShopName(request.getShopName());
        vendor.setDescription(request.getDescription());
        vendor.setLogoUrl(request.getLogoUrl());
        vendor.setEmail(request.getEmail());
        vendor.setPhone(request.getPhone());
        vendor.setCategory(categoryValue(request.getCategory()));
        vendor.setStatus(request.getStatus());
        vendor.setCccd(request.getCccd());
        vendor.setTaxCode(request.getTaxCode());
        vendor.setCccdFrontImageUrl(request.getCccdFrontImageUrl());
        vendor.setCccdBackImageUrl(request.getCccdBackImageUrl());
        vendor.setFaceImageUrl(request.getFaceImageUrl());

        return vendorRepository.save(vendor);
    }

    public Vendor getVendorById(Long vendorId) {
        return vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng với ID: " + vendorId));
    }

    public Vendor getVendorByProfileId(UUID profileId) {
        return vendorRepository.findByProfileId(profileId)
                .orElseThrow(() -> new RuntimeException("Tài khoản này hiện chưa đăng ký cửa hàng"));
    }

    public List<Vendor> getAllVendors() {
        return vendorRepository.findAll();
    }

    public Optional<Profile> findProfileByEmail(String email) {
        return profileRepository.findByEmail(normalizeEmail(email));
    }

    public boolean hasVendor(Profile profile) {
        return vendorRepository.findByProfile(profile).isPresent();
    }

    public VendorLoginResponse loginVendor(LoginRequest request) {
        String loginEmail = resolveLoginEmail(request.getIdentifier());
        Profile profile = profileRepository.findByEmail(loginEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản Vendor"));

        Vendor vendor = vendorRepository.findByProfile(profile)
                .orElseThrow(() -> new RuntimeException("Tài khoản này chưa đăng ký gian hàng Vendor"));

        java.time.ZoneId zoneVn = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        ZonedDateTime nowVn = ZonedDateTime.now(zoneVn);

        if (profile.getLockoutUntil() != null && profile.getLockoutUntil().isAfter(nowVn)) {
            long minutesLeft = java.time.Duration.between(nowVn, profile.getLockoutUntil()).toMinutes() + 1;
            throw new RuntimeException(String.format("Tài khoản đang bị tạm khóa. Vui lòng thử lại sau %d phút.", minutesLeft));
        }

        Map responseBody;
        try {
            responseBody = loginSupabase(loginEmail, request.getPassword());
        } catch (RuntimeException e) {
            if (e.getCause() instanceof org.springframework.web.client.HttpClientErrorException) {
                profile.setFailedLoginAttempts(profile.getFailedLoginAttempts() + 1);
                if (profile.getFailedLoginAttempts() >= 5) {
                    int lockoutMinutes = calculateLockoutMinutes(profile.getFailedLoginAttempts());
                    profile.setLockoutUntil(ZonedDateTime.now(zoneVn).plusMinutes(lockoutMinutes));
                    profileRepository.save(profile);

                    try {
                        otpService.sendLockoutNotificationEmail(profile.getEmail(), profile.getFailedLoginAttempts(), lockoutMinutes);
                    } catch (Exception mailEx) {
                        System.err.println("Lỗi gọi gửi email cảnh báo: " + mailEx.getMessage());
                    }

                    String hoursText = lockoutMinutes >= 60 ? (lockoutMinutes / 60) + " giờ" : "";
                    String minsText = (lockoutMinutes % 60) > 0 ? (lockoutMinutes % 60) + " phút" : "";
                    String durationText = hoursText + (hoursText.isEmpty() || minsText.isEmpty() ? "" : " ") + minsText;

                    throw new RuntimeException(String.format("Tài khoản đã bị tạm khóa %s do nhập sai mật khẩu %d lần.", durationText, profile.getFailedLoginAttempts()));
                }
                profileRepository.save(profile);
            }
            throw e;
        }

        // Reset failed login attempts on success
        if (profile.getFailedLoginAttempts() > 0 || profile.getLockoutUntil() != null) {
            profile.setFailedLoginAttempts(0);
            profile.setLockoutUntil(null);
            profileRepository.save(profile);
        }

        return new VendorLoginResponse(
                (String) responseBody.get("access_token"),
                (String) responseBody.get("refresh_token"),
                Long.valueOf(responseBody.get("expires_in").toString()),
                vendor.getId(),
                profile.getId(),
                vendor.getShopName(),
                vendor.getStatus()
        );
    }

    private Profile createProfileForNewVendor(
            VendorOnboardingRequest request,
            String email,
            String ownerPhone,
            LocalDate ownerDateOfBirth
    ) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("Mật khẩu là bắt buộc khi email chưa có tài khoản Buyer");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Mật khẩu xác nhận không khớp");
        }
        if (ownerPhone == null) {
            throw new RuntimeException("Số điện thoại chủ shop là bắt buộc khi email chưa có tài khoản Buyer");
        }
        if (profileRepository.findByPhone(ownerPhone).isPresent()) {
            throw new RuntimeException("Số điện thoại chủ shop đã được sử dụng");
        }

        UUID supabaseUserId = createSupabaseUser(email, request.getPassword());
        return profileRepository.save(Profile.builder()
                .id(supabaseUserId)
                .email(email)
                .phone(ownerPhone)
                .fullName(blankToNull(request.getOwnerFullName()))
                .dateOfBirth(ownerDateOfBirth)
                .role(Roles.customer)
                .isActive(true)
                .build());
    }

    private UUID createSupabaseUser(String email, String password) {
        try {
            String url = supabaseUrl + "/auth/v1/signup";
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseAnonKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of("email", email, "password", password);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            Map responseBody = new RestTemplate().postForEntity(url, entity, Map.class).getBody();

            String supabaseUserId = extractSupabaseUserId(responseBody);
            if (supabaseUserId == null) {
                throw new RuntimeException("Không thể lấy ID từ Supabase.");
            }
            return UUID.fromString(supabaseUserId);
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Không tạo được tài khoản Supabase: " + e.getResponseBodyAsString(), e);
        }
    }

    private Map loginSupabase(String email, String password) {
        try {
            String url = supabaseUrl + "/auth/v1/token?grant_type=password";
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseAnonKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of("email", email, "password", password);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            return new RestTemplate().postForEntity(url, entity, Map.class).getBody();
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Tài khoản hoặc mật khẩu không đúng", e);
        }
    }

    private String resolveLoginEmail(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new RuntimeException("Email hoặc số điện thoại là bắt buộc");
        }

        String normalized = identifier.trim();
        if (normalized.contains("@")) {
            return normalizeEmail(normalized);
        }

        return profileRepository.findByPhone(normalizePhone(normalized))
                .map(Profile::getEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với số điện thoại này"));
    }

    @SuppressWarnings("unchecked")
    private String extractSupabaseUserId(Map responseBody) {
        if (responseBody == null) {
            return null;
        }
        Object user = responseBody.get("user");
        if (user instanceof Map<?, ?> userMap) {
            Object id = userMap.get("id");
            return id == null ? null : id.toString();
        }
        Object id = responseBody.get("id");
        return id == null ? null : id.toString();
    }

    private void validateOnboardingRequest(VendorOnboardingRequest request, String email) {
        if (email == null) {
            throw new RuntimeException("Email là bắt buộc");
        }
        if (request.getShopName() == null || request.getShopName().isBlank()) {
            throw new RuntimeException("Tên shop là bắt buộc");
        }
        if (normalizeEmail(request.getShopEmail()) == null) {
            throw new RuntimeException("Email shop là bắt buộc");
        }
        if (normalizePhone(request.getShopPhone()) == null) {
            throw new RuntimeException("SĐT shop là bắt buộc");
        }
        if (request.getCategory() == null) {
            throw new RuntimeException("Danh mục bán hàng là bắt buộc");
        }
        if (blankToNull(request.getCccd()) == null) {
            throw new RuntimeException("Số CCCD là bắt buộc. Vui lòng xác thực CCCD trước khi đăng ký");
        }
        String taxCode = blankToNull(request.getTaxCode());
        if (taxCode == null) {
            throw new RuntimeException("Mã số thuế là bắt buộc");
        }
        if (!taxCode.matches("\\d{1,12}")) {
            throw new RuntimeException("Mã số thuế chỉ gồm chữ số và không quá 12 chữ số");
        }
        if (blankToNull(request.getOwnerDateOfBirth()) == null) {
            throw new RuntimeException("Ngày sinh chủ shop là bắt buộc. Vui lòng xác thực CCCD trước khi đăng ký");
        }
        if (blankToNull(request.getOwnerFullName()) == null) {
            throw new RuntimeException("Tên chủ shop là bắt buộc. Vui lòng xác thực CCCD trước khi đăng ký");
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

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String categoryValue(VendorCategory category) {
        return category == null ? null : category.getValue();
    }

    private LocalDate parseDateOfBirth(String rawDateOfBirth) {
        String value = blankToNull(rawDateOfBirth);
        if (value == null) {
            throw new RuntimeException("Ngày sinh chủ shop là bắt buộc");
        }

        String normalized = value.replace(".", "/").replace("-", "/");
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ISO_LOCAL_DATE
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter == DateTimeFormatter.ISO_LOCAL_DATE) {
                    return LocalDate.parse(value, formatter);
                }
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        throw new RuntimeException("Ngày sinh chủ shop không đúng định dạng hỗ trợ: " + rawDateOfBirth);
    }

    private int calculateLockoutMinutes(int failedAttempts) {
        if (failedAttempts < 5) {
            return 0;
        }
        int exponent = failedAttempts - 5;
        long minutes = 15L * (1L << exponent);
        if (minutes > 480L || minutes <= 0) {
            return 480;
        }
        return (int) Math.min(minutes, 480L);
    }
}
