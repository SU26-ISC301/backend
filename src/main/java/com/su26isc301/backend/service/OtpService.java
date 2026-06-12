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
    private final Map<String, OtpRateLimitData> rateLimitStore = new ConcurrentHashMap<>();
    private final java.security.SecureRandom random = new java.security.SecureRandom();

    @Value("${app.otp.expire-seconds:120}")
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

    @Async
    public void sendLockoutNotificationEmail(String toEmail, int failedAttempts, int lockoutMinutes) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            String hoursText = lockoutMinutes >= 60 ? (lockoutMinutes / 60) + " giờ" : "";
            String minsText = (lockoutMinutes % 60) > 0 ? (lockoutMinutes % 60) + " phút" : "";
            String durationText = hoursText + (hoursText.isEmpty() || minsText.isEmpty() ? "" : " ") + minsText;

            String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                </head>
                <body style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f9fafb; padding: 20px; color: #333; line-height: 1.6; margin: 0;">
                    <div style="max-width: 500px; margin: 0 auto; background-color: #ffffff; padding: 30px; border-radius: 10px; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
                        <div style="text-align: center; border-bottom: 2px solid #f0f0f0; padding-bottom: 15px; margin-bottom: 20px;">
                            <h2 style="color: #d93025; margin: 0;">Cảnh báo bảo mật tài khoản</h2>
                        </div>
                        <div style="font-size: 15px;">
                            <p>Xin chào,</p>
                            <p>Hệ thống ghi nhận tài khoản của bạn đã nhập sai mật khẩu liên tiếp <strong>%d lần</strong>.</p>
                            <p>Để bảo vệ thông tin cá nhân của bạn, chúng tôi đã tạm thời khóa quyền đăng nhập vào tài khoản này trong vòng <strong>%s</strong>.</p>
                            <div style="background-color: #fff3cd; border: 1px dashed #ffc107; border-radius: 6px; padding: 15px; margin: 25px 0;">
                                <p style="margin: 0; color: #856404; font-size: 14px;">
                                    ⏰ Thời gian khóa sẽ tự động kết thúc sau thời hạn trên. Nếu hành động này không phải do bạn thực hiện, xin vui lòng kiểm tra lại độ an toàn mật khẩu hoặc đổi mật khẩu sau khi tài khoản mở khóa để phòng ngừa rủi ro.
                                </p>
                            </div>
                            <p style="font-size: 13px; color: #d93025; margin-top: 20px;">⚠️ Vui lòng KHÔNG cung cấp mật khẩu hoặc mã OTP cho bất kỳ ai dưới mọi hình thức.</p>
                        </div>
                        <div style="margin-top: 30px; text-align: center; font-size: 12px; color: #999; border-top: 1px solid #eee; padding-top: 15px;">
                            <p>&copy; 2026 5bro E-commerce. Mọi quyền được bảo lưu.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(failedAttempts, durationText);

            Map<String, Object> bodyMap = Map.of(
                    "sender", Map.of("email", fromEmail, "name", "5bro E-commerce Security"),
                    "to", List.of(Map.of("email", toEmail)),
                    "subject", "Cảnh báo bảo mật: Tài khoản của bạn đang bị khóa tạm thời",
                    "htmlContent", htmlContent
            );

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(bodyMap, headers);
            restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email", httpRequest, String.class);
            System.out.println("Đã gửi email cảnh báo khóa tài khoản tới " + toEmail);

        } catch (Exception e) {
            System.err.println("Lỗi gửi mail cảnh báo khóa tài khoản: " + e.getMessage());
        }
    }

    @Async
    public void sendNewDeviceLoginEmail(String toEmail, String userAgent, String ipAddress) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            String timeString = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));

            String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                </head>
                <body style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f9fafb; padding: 20px; color: #333; line-height: 1.6; margin: 0;">
                    <div style="max-width: 500px; margin: 0 auto; background-color: #ffffff; padding: 30px; border-radius: 10px; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
                        <div style="text-align: center; border-bottom: 2px solid #f0f0f0; padding-bottom: 15px; margin-bottom: 20px;">
                            <h2 style="color: #1a73e8; margin: 0;">Cảnh báo bảo mật: Thiết bị đăng nhập mới</h2>
                        </div>
                        <div style="font-size: 15px;">
                            <p>Xin chào,</p>
                            <p>Tài khoản của bạn đã được đăng nhập từ một thiết bị hoặc trình duyệt mới mà chúng tôi chưa từng ghi nhận trước đây.</p>
                            <div style="background-color: #f8f9fa; border-left: 4px solid #1a73e8; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                <p style="margin: 0 0 8px 0; font-size: 14px;"><strong>📍 Thông tin đăng nhập:</strong></p>
                                <p style="margin: 0 0 5px 0; font-size: 13px; color: #555;"><strong>Trình duyệt/Thiết bị:</strong> %s</p>
                                <p style="margin: 0 0 5px 0; font-size: 13px; color: #555;"><strong>Địa chỉ IP:</strong> %s</p>
                                <p style="margin: 0; font-size: 13px; color: #555;"><strong>Thời gian:</strong> %s (Giờ Việt Nam)</p>
                            </div>
                            <p>Nếu đây là bạn, bạn không cần thực hiện thêm hành động nào.</p>
                            <div style="background-color: #fff3cd; border: 1px dashed #ffc107; border-radius: 6px; padding: 15px; margin: 25px 0;">
                                <p style="margin: 0; color: #856404; font-size: 14px;">
                                    ⚠️ Nếu bạn không thực hiện đăng nhập này, tài khoản của bạn có thể đã bị xâm nhập. Vui lòng <strong>đổi mật khẩu ngay lập tức</strong> và liên hệ với chúng tôi để được hỗ trợ khóa tài khoản tạm thời.
                                </p>
                            </div>
                        </div>
                        <div style="margin-top: 30px; text-align: center; font-size: 12px; color: #999; border-top: 1px solid #eee; padding-top: 15px;">
                            <p>&copy; 2026 5bro E-commerce. Mọi quyền được bảo lưu.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(userAgent != null ? userAgent : "Không xác định", ipAddress != null ? ipAddress : "Không xác định", timeString);

            Map<String, Object> bodyMap = Map.of(
                    "sender", Map.of("email", fromEmail, "name", "5bro E-commerce Security"),
                    "to", List.of(Map.of("email", toEmail)),
                    "subject", "Cảnh báo bảo mật: Phát hiện đăng nhập từ thiết bị mới",
                    "htmlContent", htmlContent
            );

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(bodyMap, headers);
            restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email", httpRequest, String.class);
            System.out.println("Đã gửi email cảnh báo thiết bị mới tới " + toEmail);

        } catch (Exception e) {
            System.err.println("Lỗi gửi mail cảnh báo thiết bị mới: " + e.getMessage());
        }
    }

    @Async
    public void sendSubscriptionConfirmationEmail(String toEmail, String shopName, String planType, long amount, int expiryDays) {
        try {
            int displayExpiryDays = 30;
            String planLabel = "plus".equalsIgnoreCase(planType) ? "Plus" : "Premium";
            String headerGradient = "plus".equalsIgnoreCase(planType) 
                    ? "linear-gradient(135deg,#f97316,#ea580c)" 
                    : "linear-gradient(135deg,#8b5cf6,#6d28d9)";
            String tableBg = "plus".equalsIgnoreCase(planType) ? "#fff7ed" : "#f5f3ff";
            String tableBorder = "plus".equalsIgnoreCase(planType) ? "#fed7aa" : "#ddd6fe";

            String htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head><meta charset="UTF-8"></head>
                    <body style="font-family: 'Segoe UI', Tahoma, sans-serif; background:#f9fafb; padding:20px; margin:0;">
                      <div style="max-width:560px;margin:0 auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                        <div style="background:%s;padding:32px;text-align:center;">
                          <h1 style="color:#fff;margin:0;font-size:24px;">🎉 Thanh toán thành công!</h1>
                          <p style="color:rgba(255,255,255,0.85);margin:8px 0 0;">Gói %s đã được kích hoạt</p>
                        </div>
                        <div style="padding:28px;">
                          <p style="font-size:15px;color:#374151;">Xin chào <strong>%s</strong>,</p>
                          <p style="font-size:15px;color:#374151;">Gói <strong>%s</strong> của bạn đã được kích hoạt thành công.</p>
                          <div style="background:%s;border:1px solid %s;border-radius:8px;padding:16px;margin:20px 0;">
                            <table style="width:100%%;font-size:14px;color:#374151;">
                              <tr><td style="padding:4px 0;">📦 Gói đăng ký:</td><td style="text-align:right;font-weight:bold;">%s</td></tr>
                              <tr><td style="padding:4px 0;">💰 Số tiền:</td><td style="text-align:right;font-weight:bold;">%,d VNĐ</td></tr>
                              <tr><td style="padding:4px 0;">⏰ Hiệu lực:</td><td style="text-align:right;font-weight:bold;">%d ngày</td></tr>
                            </table>
                          </div>
                          <p style="font-size:13px;color:#6b7280;margin-top:24px;">Nếu có thắc mắc, vui lòng liên hệ hỗ trợ qua email này.</p>
                        </div>
                        <div style="background:#f9fafb;padding:16px;text-align:center;font-size:12px;color:#9ca3af;">
                          © 2026 5bro E-commerce. Mọi quyền được bảo lưu.
                        </div>
                      </div>
                    </body>
                    </html>
                    """.formatted(headerGradient, planLabel, shopName, planLabel, tableBg, tableBorder, planLabel, amount, displayExpiryDays);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            Map<String, Object> body = Map.of(
                    "sender", Map.of("email", fromEmail, "name", "ShopVN"),
                    "to", List.of(Map.of("email", toEmail, "name", shopName)),
                    "subject", "✅ Kích hoạt gói " + planLabel + " thành công – ShopVN",
                    "htmlContent", htmlContent
            );

            restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email",
                    new HttpEntity<>(body, headers), String.class);

            System.out.println("📧 Đã gửi email xác nhận gói " + planType + " cho email " + toEmail);
        } catch (Exception e) {
            System.err.println("Lỗi gửi email xác nhận subscription: " + e.getMessage());
        }
    }

    public void checkAndIncrementOtpRateLimit(String email) {
        String normalized = email.trim().toLowerCase();
        LocalDateTime now = LocalDateTime.now();
        
        OtpRateLimitData data = rateLimitStore.computeIfAbsent(normalized, k -> new OtpRateLimitData(now.minusSeconds(70), 0, now.plusDays(1)));
        
        // Reset daily count if reset time has passed
        if (now.isAfter(data.resetTime)) {
            data.countToday = 0;
            data.resetTime = now.plusDays(1);
        }
        
        // Check daily limit
        if (data.countToday >= 5) {
            throw new RuntimeException("Bạn đã vượt quá giới hạn gửi 5 OTP trong một ngày cho email này. Vui lòng thử lại vào ngày mai.");
        }
        
        // Check 60-second limit
        long secondsPassed = java.time.Duration.between(data.lastSentAt, now).toSeconds();
        if (secondsPassed < 60) {
            throw new RuntimeException("Vui lòng đợi " + (60 - secondsPassed) + " giây trước khi yêu cầu gửi lại mã OTP.");
        }
        
        // Increment count and update last sent time
        data.lastSentAt = now;
        data.countToday += 1;
    }

    private record OtpData(String code, LocalDateTime expiresAt, RegisterRequest payload) {}

    private static class OtpRateLimitData {
        private LocalDateTime lastSentAt;
        private int countToday;
        private LocalDateTime resetTime;

        public OtpRateLimitData(LocalDateTime lastSentAt, int countToday, LocalDateTime resetTime) {
            this.lastSentAt = lastSentAt;
            this.countToday = countToday;
            this.resetTime = resetTime;
        }
    }

    @Scheduled(fixedRate = 900000)
    public void cleanupExpiredOtps() {
        LocalDateTime now = LocalDateTime.now();
        otpStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
        verifiedEmailStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
        rateLimitStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue().resetTime));
    }
}
