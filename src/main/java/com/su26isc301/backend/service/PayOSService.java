package com.su26isc301.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Tích hợp PayOS – https://payos.vn
 * Sandbox dashboard: https://my.payos.vn (tab "Thử nghiệm")
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayOSService {

    private static final String PAYOS_BASE_URL = "https://api-merchant.payos.vn";

    @Value("${payos.client-id:DEMO_CLIENT_ID}")
    private String clientId;

    @Value("${payos.api-key:DEMO_API_KEY}")
    private String apiKey;

    @Value("${payos.checksum-key:DEMO_CHECKSUM_KEY}")
    private String checksumKey;

    @Value("${payos.return-url:http://localhost:3000/vendor/subscription/callback}")
    private String returnUrl;

    @Value("${payos.cancel-url:http://localhost:3000/vendor/san-pham}")
    private String cancelUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Tạo payment link PayOS
     *
     * @param orderCode  Mã đơn nội bộ (số nguyên, unique)
     * @param amount     Số tiền VNĐ
     * @param description Mô tả (tối đa 25 ký tự)
     * @param vendorId   ID vendor để truyền vào extraData
     * @param planType   Gói: 'plus' hoặc 'premium'
     * @return URL thanh toán
     */
    @SuppressWarnings("unchecked")
    public String createPaymentLink(long orderCode, long amount, String description,
                                    long vendorId, String planType) {
        try {
            // Bước 1: Tạo chữ ký
            String data = "amount=" + amount +
                    "&cancelUrl=" + cancelUrl +
                    "&description=" + description +
                    "&orderCode=" + orderCode +
                    "&returnUrl=" + returnUrl;

            String signature = hmacSHA256(data, checksumKey);

            // Bước 2: Build request body
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("orderCode", orderCode);
            body.put("amount", (int) amount);
            body.put("description", description);
            body.put("returnUrl", returnUrl);
            body.put("cancelUrl", cancelUrl);
            body.put("signature", signature);

            // Bước 3: Gọi API PayOS
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-client-id", clientId);
            headers.set("x-api-key", apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    PAYOS_BASE_URL + "/v2/payment-requests",
                    request,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("PayOS trả về response rỗng");
            }

            String code = (String) responseBody.get("code");
            if (!"00".equals(code)) {
                String desc = (String) responseBody.get("desc");
                throw new RuntimeException("PayOS lỗi: " + desc);
            }

            Map<String, Object> data2 = (Map<String, Object>) responseBody.get("data");
            return (String) data2.get("checkoutUrl");

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi tạo PayOS payment link", e);
            throw new RuntimeException("Không thể tạo link thanh toán PayOS: " + e.getMessage());
        }
    }

    /**
     * Xác thực webhook từ PayOS dựa vào chữ ký HMAC-SHA256
     */
    public boolean verifyWebhookSignature(Map<String, Object> webhookData, String receivedSignature) {
        try {
            // PayOS ký trên chuỗi data sắp xếp theo thứ tự key
            TreeMap<String, Object> sorted = new TreeMap<>(webhookData);
            StringBuilder dataStr = new StringBuilder();
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!"signature".equals(entry.getKey())) {
                    if (!dataStr.isEmpty()) dataStr.append("&");
                    dataStr.append(entry.getKey()).append("=").append(entry.getValue());
                }
            }
            String expectedSignature = hmacSHA256(dataStr.toString(), checksumKey);
            return expectedSignature.equals(receivedSignature);
        } catch (Exception e) {
            log.error("Lỗi verify PayOS signature", e);
            return false;
        }
    }

    /**
     * Lấy thông tin đơn hàng từ PayOS (để polling status)
     */
    @SuppressWarnings("unchecked")
    public String getPaymentStatus(long orderCode) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", clientId);
            headers.set("x-api-key", apiKey);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<Map> response = restTemplate.exchange(
                    PAYOS_BASE_URL + "/v2/payment-requests/" + orderCode,
                    HttpMethod.GET,
                    request,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return "UNKNOWN";

            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data == null) return "UNKNOWN";

            return (String) data.get("status"); // PENDING | PAID | CANCELLED | EXPIRED
        } catch (Exception e) {
            log.error("Lỗi query PayOS status cho orderCode={}", orderCode, e);
            return "UNKNOWN";
        }
    }

    // ─────────── HMAC-SHA256 helper ───────────

    private String hmacSHA256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
