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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> verifiedEmailStore = new ConcurrentHashMap<>();
    private final java.security.SecureRandom random = new java.security.SecureRandom();

    @Value("${app.otp.expire-seconds:60}")
    private long otpExpireSeconds;

    @Value("${app.vendor.email-verified-seconds:600}")
    private long verifiedEmailExpireSeconds;

    @Value("${app.mail.sender}")
    private String fromEmail;

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Async
    public void generateAndSendOtp(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String otp = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(otpExpireSeconds);

        otpStore.put(email, new OtpData(otp, expiresAt, request));

        sendEmailViaApi(email, otp);
    }

    @Async
    public void generateAndSendOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        String otp = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(otpExpireSeconds);

        otpStore.put(normalizedEmail, new OtpData(otp, expiresAt, null));

        sendEmailViaApi(normalizedEmail, otp);
    }

    private void sendEmailViaApi(String toEmail, String otp) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                </head>
                <body style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f9fafb; padding: 20px; color: #333; line-height: 1.6; margin: 0;">
                    <div style="max-width: 500px; margin: 0 auto; background-color: #ffffff; padding: 30px; border-radius: 10px; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
                        <div style="text-align: center; border-bottom: 2px solid #f0f0f0; padding-bottom: 15px; margin-bottom: 20px;">
                            <h2 style="color: #1a73e8; margin: 0;">5bro E-commerce</h2>
                        </div>
                        <div style="font-size: 15px;">
                            <p>Xin chào,</p>
                            <p>Bạn vừa yêu cầu mã OTP để xác thực tài khoản. Vui lòng sử dụng mã dưới đây để tiếp tục:</p>
                            <div style="background-color: #f0f7ff; border: 1px dashed #1a73e8; border-radius: 6px; padding: 15px; text-align: center; margin: 25px 0;">
                                <div style="font-size: 32px; font-weight: bold; color: #1a73e8; letter-spacing: 8px;">%s</div>
                            </div>
                            <p>Mã này có hiệu lực trong vòng <strong>%d giây</strong>.</p>
                            <p style="font-size: 13px; color: #d93025; margin-top: 20px;">⚠️ Tuyệt đối không chia sẻ mã này cho bất kỳ ai để bảo vệ tài khoản của bạn.</p>
                            <p>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.</p>
                        </div>
                        <div style="margin-top: 30px; text-align: center; font-size: 12px; color: #999; border-top: 1px solid #eee; padding-top: 15px;">
                            <p>&copy; 2026 5bro E-commerce. Mọi quyền được bảo lưu.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(otp, otpExpireSeconds);

            // Xây dựng JSON body thông qua Map và List để tránh lỗi format chuỗi
            Map<String, Object> bodyMap = Map.of(
                    "sender", Map.of("email", fromEmail, "name", "5bro E-commerce"),
                    "to", List.of(Map.of("email", toEmail)),
                    "bcc", List.of(Map.of("email", fromEmail)),
                    "subject", "Mã OTP xác thực tài khoản",
                    "htmlContent", htmlContent
            );

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(bodyMap, headers);
            restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email", httpRequest, String.class);
            System.out.println("Đã gửi OTP qua API tới " + toEmail + " và BCC về " + fromEmail);

        } catch (Exception e) {
            System.err.println("Lỗi gửi mail qua API: " + e.getMessage());
        }
    }

    public RegisterRequest verifyAndGetPayload(String email, String otp) {
        OtpData otpData = findValidOtp(email);
        if (otpData == null) {
            return null;
        }

        if (otpData.code().equals(otp)) {
            return otpData.payload();
        }
        return null;
    }

    public boolean verifyOtp(String email, String otp) {
        OtpData otpData = findValidOtp(email);
        return otpData != null && otpData.code().equals(otp);
    }

    public void markEmailVerified(String email) {
        verifiedEmailStore.put(email.trim().toLowerCase(), LocalDateTime.now().plusSeconds(verifiedEmailExpireSeconds));
    }

    public boolean isEmailVerified(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        LocalDateTime expiresAt = verifiedEmailStore.get(normalizedEmail);
        if (expiresAt == null || LocalDateTime.now().isAfter(expiresAt)) {
            verifiedEmailStore.remove(normalizedEmail);
            return false;
        }
        return true;
    }

    private OtpData findValidOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        OtpData otpData = otpStore.get(normalizedEmail);
        if (otpData == null || LocalDateTime.now().isAfter(otpData.expiresAt())) {
            otpStore.remove(normalizedEmail);
            return null;
        }

        return otpData;
    }

    public void removeOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        otpStore.remove(normalizedEmail);
        verifiedEmailStore.remove(normalizedEmail);
    }

    private record OtpData(String code, LocalDateTime expiresAt, RegisterRequest payload) {}

    @Scheduled(fixedRate = 900000)
    public void cleanupExpiredOtps() {
        LocalDateTime now = LocalDateTime.now();
        otpStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
        verifiedEmailStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }
}
