package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.request.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OtpService {
//    private final JavaMailSender mailSender;
//    private final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();
//    private final java.security.SecureRandom random = new java.security.SecureRandom();
//    @Value("${app.otp.expire-minutes:10}")
//    private long otpExpireMinutes;
//
//    @Value("${spring.mail.username}")
//    private String fromEmail;
//
//    @Async
//    public void generateAndSendOtp(String email) {
//        String otp = String.format("%06d", random.nextInt(1_000_000));
//        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpireMinutes);
//        otpStore.put(email, new OtpData(otp, expiresAt));
//
//        SimpleMailMessage mail = new SimpleMailMessage();
//        mail.setFrom(fromEmail);
//        mail.setTo(email);
//        mail.setSubject("Mã OTP xác thực tài khoản");
//        mail.setText("Mã OTP của bạn là: " + otp + "\nMã có hiệu lực trong " + otpExpireMinutes + " phút.");
//        mailSender.send(mail);
//    }
//
//    public boolean verifyOtp(String email, String otp) {
//        OtpData otpData = otpStore.get(email);
//        if (otpData == null) {
//            return false;
//        }
//
//        if (LocalDateTime.now().isAfter(otpData.expiresAt())) {
//            otpStore.remove(email);
//            return false;
//        }
//
//        boolean matched = otpData.code().equals(otp);
//        if (matched) {
//            otpStore.remove(email);
//        }
//        return matched;
//    }
//
//    private record OtpData(String code, LocalDateTime expiresAt) {
//    }
//
//    @Scheduled(fixedRate = 900000)
//    public void cleanupExpiredOtps() {
//        LocalDateTime now = LocalDateTime.now();
//        otpStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
//    }
        private final JavaMailSender mailSender;
        private final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();
        private final java.security.SecureRandom random = new java.security.SecureRandom();

        @Value("${app.otp.expire-seconds:60}")
        private long otpExpireSeconds;

        @Value("${spring.mail.username}")
        private String fromEmail;

        @Async
        public void generateAndSendOtp(RegisterRequest request) {
            String email = request.getEmail();
            String otp = String.format("%06d", random.nextInt(1_000_000));
            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(otpExpireSeconds);

            // LƯU CẢ THÔNG TIN ĐĂNG KÝ (request) VÀO RAM CÙNG VỚI OTP
            otpStore.put(email, new OtpData(otp, expiresAt, request));

            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(fromEmail);
            mail.setTo(email);
            mail.setSubject("Mã OTP xác thực tài khoản");
            mail.setText("Mã OTP của bạn là: " + otp + "\nMã có hiệu lực trong " + otpExpireSeconds + " giây.");
            mailSender.send(mail);
        }

        // Hàm này trả về RegisterRequest nếu OTP đúng, trả về null nếu sai
        public RegisterRequest verifyAndGetPayload(String email, String otp) {
            OtpData otpData = otpStore.get(email);
            if (otpData == null || LocalDateTime.now().isAfter(otpData.expiresAt())) {
                otpStore.remove(email);
                return null;
            }

            if (otpData.code().equals(otp)) {
                RegisterRequest payload = otpData.payload();
                otpStore.remove(email);
                return payload;
            }
            return null;
        }

        private record OtpData(String code, LocalDateTime expiresAt, RegisterRequest payload) {}

        @Scheduled(fixedRate = 900000)
        public void cleanupExpiredOtps() {
            LocalDateTime now = LocalDateTime.now();
            otpStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
        }
}