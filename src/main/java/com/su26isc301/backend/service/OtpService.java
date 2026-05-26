package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.request.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();
    private final java.security.SecureRandom random = new java.security.SecureRandom();

    @Value("${app.otp.expire-seconds:60}")
    private long otpExpireSeconds;

    @Value("${app.mail.sender}")
    private String fromEmail;

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Async
    public void generateAndSendOtp(RegisterRequest request) {
        String email = request.getEmail();
        String otp = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(otpExpireSeconds);

        otpStore.put(email, new OtpData(otp, expiresAt, request));

        sendEmailViaApi(email, otp);
    }

    private void sendEmailViaApi(String toEmail, String otp) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            String textContent = "Mã OTP xác thực tài khoản của bạn là: " + otp + ". Mã có hiệu lực trong " + otpExpireSeconds + " giây.";

            String body = """
                {
                   "sender": { "email": "%s" },
                   "to": [ { "email": "%s" } ],
                   "subject": "Mã OTP xác thực tài khoản",
                   "textContent": "%s"
                }
                """.formatted(fromEmail, toEmail, textContent);

            HttpEntity<String> httpRequest = new HttpEntity<>(body, headers);
            restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email", httpRequest, String.class);
            System.out.println("Đã gửi OTP thành công qua API tới: " + toEmail);

        } catch (Exception e) {
            System.err.println("Lỗi gửi mail qua API: " + e.getMessage());
        }
    }

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