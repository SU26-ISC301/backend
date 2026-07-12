package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.request.ConfirmChangePinRequest;
import com.su26isc301.backend.dto.request.SetupPinConfirmRequest;
import com.su26isc301.backend.dto.response.WalletPinStatusResponse;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.exception.BusinessException;
import com.su26isc301.backend.exception.ResourceNotFoundException;
import com.su26isc301.backend.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class WalletPinService {

    private final VendorRepository vendorRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    // Bộ lưu trữ Token xác thực tạm thời trong bộ nhớ
    private final Map<String, TokenData> tokenStore = new ConcurrentHashMap<>();

    private static final Set<String> WEAK_PINS = Set.of(
            "000000", "111111", "222222", "333333", "444444",
            "555555", "666666", "777777", "888888", "999999",
            "123456", "654321"
    );

    private record TokenData(Long vendorId, String email, String purpose, ZonedDateTime expiresAt) {}

    @Transactional(readOnly = true)
    public WalletPinStatusResponse getPinStatus(String email) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa liên kết cửa hàng"));

        return WalletPinStatusResponse.builder()
                .enabled(Boolean.TRUE.equals(vendor.getWalletPinEnabled()))
                .createdAt(vendor.getWalletPinCreatedAt())
                .updatedAt(vendor.getWalletPinUpdatedAt())
                .lockedUntil(vendor.getWalletPinLockedUntil())
                .build();
    }

    public void requestSetupOtp(String email) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa liên kết cửa hàng"));

        if (Boolean.TRUE.equals(vendor.getWalletPinEnabled())) {
            throw new BusinessException("PIN_ALREADY_SET", "Mã PIN ví đã được kích hoạt trước đó");
        }

        // Kiểm tra và gửi OTP thông qua OtpService
        otpService.checkAndIncrementOtpRateLimit(email);
        otpService.generateAndSendOtp(email);
    }

    public String verifySetupOtp(String email, String otp) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa liên kết cửa hàng"));

        if (Boolean.TRUE.equals(vendor.getWalletPinEnabled())) {
            throw new BusinessException("PIN_ALREADY_SET", "Mã PIN ví đã được kích hoạt trước đó");
        }

        boolean isValid = otpService.verifyOtp(email, otp);
        if (!isValid) {
            throw new BusinessException("INVALID_OTP", "Mã OTP không chính xác hoặc đã hết hạn");
        }

        otpService.removeOtp(email);

        // Tạo setup token tạm thời có hạn 10 phút
        String token = UUID.randomUUID().toString();
        tokenStore.put(token, new TokenData(vendor.getId(), email, "WALLET_PIN_SETUP", ZonedDateTime.now().plusMinutes(10)));

        return token;
    }

    @Transactional
    public void confirmSetupPin(String email, SetupPinConfirmRequest request) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa liên kết cửa hàng"));

        TokenData tokenData = tokenStore.get(request.getPinSetupToken());
        if (tokenData == null || tokenData.expiresAt().isBefore(ZonedDateTime.now())
                || !"WALLET_PIN_SETUP".equals(tokenData.purpose())
                || !tokenData.vendorId().equals(vendor.getId())) {
            throw new BusinessException("PIN_TOKEN_EXPIRED", "Phiên xác thực kích hoạt mã PIN đã hết hạn hoặc không hợp lệ");
        }

        tokenStore.remove(request.getPinSetupToken());

        // Validate PIN định dạng
        validatePinStrength(request.getNewPin());

        if (!request.getNewPin().equals(request.getConfirmPin())) {
            throw new BusinessException("PIN_CONFIRM_NOT_MATCH", "Mã PIN xác nhận không khớp");
        }

        // Mã hóa và lưu PIN
        vendor.setWalletPinHash(passwordEncoder.encode(request.getNewPin()));
        vendor.setWalletPinEnabled(true);
        vendor.setWalletPinCreatedAt(ZonedDateTime.now());
        vendor.setWalletPinUpdatedAt(ZonedDateTime.now());
        vendor.setWalletPinFailedAttempts(0);
        vendor.setWalletPinLockedUntil(null);

        vendorRepository.save(vendor);
    }

    @Transactional
    public String verifyCurrentPin(String email, String currentPin) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa liên kết cửa hàng"));

        if (!Boolean.TRUE.equals(vendor.getWalletPinEnabled())) {
            throw new BusinessException("PIN_NOT_SET", "Mã PIN ví chưa được kích hoạt");
        }

        // Kiểm tra xem PIN có đang bị khóa không
        if (vendor.getWalletPinLockedUntil() != null && vendor.getWalletPinLockedUntil().isAfter(ZonedDateTime.now())) {
            throw new BusinessException("PIN_LOCKED", "Mã PIN ví đang bị tạm khóa. Vui lòng thử lại sau.");
        }

        boolean matches = passwordEncoder.matches(currentPin, vendor.getWalletPinHash());
        if (!matches) {
            int currentAttempts = vendor.getWalletPinFailedAttempts() != null ? vendor.getWalletPinFailedAttempts() : 0;
            int attempts = currentAttempts + 1;
            vendor.setWalletPinFailedAttempts(attempts);

            if (attempts >= 5) {
                vendor.setWalletPinLockedUntil(ZonedDateTime.now().plusMinutes(15));
                vendorRepository.save(vendor);
                throw new BusinessException("PIN_LOCKED", "Nhập sai mã PIN quá 5 lần. Xác thực giao dịch bị tạm khóa trong 15 phút.");
            } else {
                vendorRepository.save(vendor);
                throw new BusinessException("INVALID_PIN", "Mã PIN không chính xác. Bạn còn " + (5 - attempts) + " lần thử.");
            }
        }

        // Nhập đúng: reset failed attempts
        vendor.setWalletPinFailedAttempts(0);
        vendor.setWalletPinLockedUntil(null);
        vendorRepository.save(vendor);

        // Tạo token tạm thời cho bước tiếp theo của thay đổi PIN
        String token = UUID.randomUUID().toString();
        tokenStore.put(token, new TokenData(vendor.getId(), email, "WALLET_PIN_CHANGE_VERIFIED", ZonedDateTime.now().plusMinutes(10)));

        return token;
    }

    public void requestChangeOtp(String email, String currentPinToken) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa liên kết cửa hàng"));

        TokenData tokenData = tokenStore.get(currentPinToken);
        if (tokenData == null || tokenData.expiresAt().isBefore(ZonedDateTime.now())
                || !"WALLET_PIN_CHANGE_VERIFIED".equals(tokenData.purpose())
                || !tokenData.vendorId().equals(vendor.getId())) {
            throw new BusinessException("PIN_TOKEN_EXPIRED", "Phiên xác thực thay đổi mã PIN đã hết hạn hoặc không hợp lệ");
        }

        otpService.checkAndIncrementOtpRateLimit(email);
        otpService.generateAndSendOtp(email);
    }

    public String verifyChangeOtp(String email, String currentPinToken, String otp) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa liên kết cửa hàng"));

        TokenData tokenData = tokenStore.get(currentPinToken);
        if (tokenData == null || tokenData.expiresAt().isBefore(ZonedDateTime.now())
                || !"WALLET_PIN_CHANGE_VERIFIED".equals(tokenData.purpose())
                || !tokenData.vendorId().equals(vendor.getId())) {
            throw new BusinessException("PIN_TOKEN_EXPIRED", "Phiên xác thực thay đổi mã PIN đã hết hạn hoặc không hợp lệ");
        }

        boolean isValid = otpService.verifyOtp(email, otp);
        if (!isValid) {
            throw new BusinessException("INVALID_OTP", "Mã OTP không chính xác hoặc đã hết hạn");
        }

        otpService.removeOtp(email);
        tokenStore.remove(currentPinToken);

        // Tạo change token cho bước thiết lập PIN mới
        String changeToken = UUID.randomUUID().toString();
        tokenStore.put(changeToken, new TokenData(vendor.getId(), email, "WALLET_PIN_CHANGE_AUTHORIZED", ZonedDateTime.now().plusMinutes(10)));

        return changeToken;
    }

    @Transactional
    public void confirmChangePin(String email, ConfirmChangePinRequest request) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa liên kết cửa hàng"));

        TokenData tokenData = tokenStore.get(request.getPinChangeToken());
        if (tokenData == null || tokenData.expiresAt().isBefore(ZonedDateTime.now())
                || !"WALLET_PIN_CHANGE_AUTHORIZED".equals(tokenData.purpose())
                || !tokenData.vendorId().equals(vendor.getId())) {
            throw new BusinessException("PIN_TOKEN_EXPIRED", "Phiên xác thực đổi mã PIN đã hết hạn hoặc không hợp lệ");
        }

        tokenStore.remove(request.getPinChangeToken());

        validatePinStrength(request.getNewPin());

        if (!request.getNewPin().equals(request.getConfirmPin())) {
            throw new BusinessException("PIN_CONFIRM_NOT_MATCH", "Mã PIN xác nhận không khớp");
        }

        // Mã PIN mới không được trùng mã PIN cũ
        if (passwordEncoder.matches(request.getNewPin(), vendor.getWalletPinHash())) {
            throw new BusinessException("PIN_WEAK", "Mã PIN mới không được trùng với mã PIN hiện tại");
        }

        // Lưu PIN mới
        vendor.setWalletPinHash(passwordEncoder.encode(request.getNewPin()));
        vendor.setWalletPinUpdatedAt(ZonedDateTime.now());
        vendor.setWalletPinFailedAttempts(0);
        vendor.setWalletPinLockedUntil(null);

        vendorRepository.save(vendor);
    }

    /**
     * Xác thực mã PIN khi thực hiện giao dịch ví nội bộ.
     */
    @Transactional
    public void verifyWalletPin(Long vendorId, String walletPin) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin cửa hàng"));

        if (!Boolean.TRUE.equals(vendor.getWalletPinEnabled())) {
            throw new BusinessException("PIN_NOT_SET", "Vui lòng kích hoạt mã PIN ví để thực hiện giao dịch");
        }

        if (walletPin == null || walletPin.trim().isEmpty()) {
            throw new BusinessException("PIN_REQUIRED", "Vui lòng nhập mã PIN ví để xác thực giao dịch");
        }

        // Kiểm tra xem PIN có đang bị khóa không
        if (vendor.getWalletPinLockedUntil() != null && vendor.getWalletPinLockedUntil().isAfter(ZonedDateTime.now())) {
            throw new BusinessException("PIN_LOCKED", "Mã PIN ví đang bị tạm khóa. Vui lòng thử lại sau.");
        }

        boolean matches = passwordEncoder.matches(walletPin, vendor.getWalletPinHash());
        if (!matches) {
            int currentAttempts = vendor.getWalletPinFailedAttempts() != null ? vendor.getWalletPinFailedAttempts() : 0;
            int attempts = currentAttempts + 1;
            vendor.setWalletPinFailedAttempts(attempts);

            if (attempts >= 5) {
                vendor.setWalletPinLockedUntil(ZonedDateTime.now().plusMinutes(15));
                vendorRepository.save(vendor);
                throw new BusinessException("PIN_LOCKED", "Nhập sai mã PIN quá 5 lần. Xác thực giao dịch bị khóa trong 15 phút.");
            } else {
                vendorRepository.save(vendor);
                throw new BusinessException("INVALID_PIN", "Mã PIN không chính xác. Bạn còn " + (5 - attempts) + " lần thử.");
            }
        }

        // Reset attempts
        if (vendor.getWalletPinFailedAttempts() != null && vendor.getWalletPinFailedAttempts() > 0) {
            vendor.setWalletPinFailedAttempts(0);
            vendorRepository.save(vendor);
        }
    }

    private void validatePinStrength(String pin) {
        if (pin == null || !pin.matches("^\\d{6}$")) {
            throw new BusinessException("PIN_WEAK", "Mã PIN phải gồm đúng 6 chữ số");
        }
        if (WEAK_PINS.contains(pin)) {
            throw new BusinessException("PIN_WEAK", "Mã PIN quá đơn giản hoặc dễ đoán");
        }
    }
}
