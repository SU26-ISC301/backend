package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.request.VendorRegisterRequest;
import com.su26isc301.backend.dto.request.VendorOnboardingRequest;
import com.su26isc301.backend.dto.request.VendorUpdateRequest;
import com.su26isc301.backend.dto.response.CccdFaceVerificationResponse;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.enums.Roles;
import com.su26isc301.backend.enums.VendorCategory;
import com.su26isc301.backend.repository.ProfileRepository;
import com.su26isc301.backend.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

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
    private final FptCccdVerificationService fptCccdVerificationService;

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

        return vendorRepository.save(vendor);
    }

    @Transactional
    public Vendor registerVendorOnboarding(
            VendorOnboardingRequest request,
            MultipartFile frontImage,
            MultipartFile backImage,
            MultipartFile faceImage
    ) {
        String email = normalizeEmail(request.getEmail());
        String ownerPhone = normalizePhone(request.getOwnerPhone());
        validateOnboardingRequest(request, email);

        CccdFaceVerificationResponse verification = fptCccdVerificationService.verifyWithFace(
                frontImage,
                backImage,
                faceImage
        );
        if (!verification.isVerified()) {
            throw new RuntimeException(verification.getMessage());
        }
        LocalDate ownerDateOfBirth = extractOwnerDateOfBirth(verification);

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
        if (profile.getDateOfBirth() == null) {
            profile.setDateOfBirth(ownerDateOfBirth);
        }

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
                .cccd(verification.getCccd().getCccdNumber())
                .taxCode(blankToNull(request.getTaxCode()))
                .build();

        return vendorRepository.save(vendor);
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
                .fullName(email.substring(0, email.indexOf("@")))
                .dateOfBirth(ownerDateOfBirth)
                .role(Roles.customer)
                .isActive(true)
                .build());
    }

    private UUID createSupabaseUser(String email, String password) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = supabaseUrl + "/auth/v1/signup";
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseAnonKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of("email", email, "password", password);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            Map responseBody = restTemplate.postForEntity(url, entity, Map.class).getBody();

            String supabaseUserId = extractSupabaseUserId(responseBody);
            if (supabaseUserId == null) {
                throw new RuntimeException("Không thể lấy ID từ Supabase.");
            }
            return UUID.fromString(supabaseUserId);
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Không tạo được tài khoản Supabase: " + e.getResponseBodyAsString(), e);
        }
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

    private LocalDate extractOwnerDateOfBirth(CccdFaceVerificationResponse verification) {
        Map<String, Object> frontData = Optional.ofNullable(verification.getCccd())
                .map(cccd -> cccd.getFront())
                .map(front -> front.getExtractedData())
                .orElse(Map.of());

        String rawDateOfBirth = firstNonBlank(
                frontData,
                "dob",
                "date_of_birth",
                "dateOfBirth",
                "birthday",
                "birth_day",
                "birthDate",
                "ngay_sinh"
        );
        if (rawDateOfBirth == null) {
            throw new RuntimeException("Không đọc được ngày sinh từ CCCD. Vui lòng kiểm tra lại ảnh mặt trước CCCD");
        }

        return parseDateOfBirth(rawDateOfBirth);
    }

    private LocalDate parseDateOfBirth(String rawDateOfBirth) {
        String normalized = rawDateOfBirth.trim().replace(".", "/").replace("-", "/");
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ISO_LOCAL_DATE
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter == DateTimeFormatter.ISO_LOCAL_DATE) {
                    return LocalDate.parse(rawDateOfBirth.trim(), formatter);
                }
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        throw new RuntimeException("Ngày sinh OCR từ CCCD không đúng định dạng hỗ trợ: " + rawDateOfBirth);
    }

    private String firstNonBlank(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !value.toString().isBlank() && !"N/A".equalsIgnoreCase(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }
}
