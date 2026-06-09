package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.SubscriptionUpgradeRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.PaymentLinkResponse;
import com.su26isc301.backend.dto.response.SubscriptionStatusResponse;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.repository.VendorRepository;
import com.su26isc301.backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final VendorRepository vendorRepository;

    /**
     * GET /api/subscription/status
     * Lấy trạng thái gói đăng ký hiện tại (cần đăng nhập)
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> getStatus(
            Authentication authentication
    ) {
        Long vendorId = resolveVendorId(authentication);
        SubscriptionStatusResponse status = subscriptionService.getSubscriptionStatus(vendorId);
        return ResponseEntity.ok(ApiResponse.success("Lấy trạng thái gói thành công", status));
    }

    /**
     * POST /api/subscription/upgrade
     * Tạo link thanh toán để nâng cấp gói (cần đăng nhập)
     * Body: { "planType": "plus|premium", "paymentMethod": "payos" }
     */
    @PostMapping("/upgrade")
    public ResponseEntity<ApiResponse<PaymentLinkResponse>> createUpgrade(
            Authentication authentication,
            @RequestBody SubscriptionUpgradeRequest request
    ) {
        try {
            Long vendorId = resolveVendorId(authentication);
            PaymentLinkResponse link = subscriptionService.createUpgradePaymentLink(
                    vendorId, request.getPlanType(), request.getPaymentMethod()
            );
            return ResponseEntity.ok(ApiResponse.success("Tạo link thanh toán thành công", link));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/subscription/check-payment?orderCode=xxx
     * FE polling để biết thanh toán xong chưa
     */
    @GetMapping("/check-payment")
    public ResponseEntity<ApiResponse<Map<String, String>>> checkPayment(
            Authentication authentication,
            @RequestParam String orderCode
    ) {
        Long vendorId = resolveVendorId(authentication);
        String status = subscriptionService.checkPaymentResult(orderCode, vendorId);
        return ResponseEntity.ok(ApiResponse.success("Kiểm tra thanh toán", Map.of("status", status)));
    }

    /**
     * POST /api/subscription/webhook/payos
     * PayOS gọi vào đây khi có kết quả thanh toán (KHÔNG cần auth)
     */
    @PostMapping("/webhook/payos")
    public ResponseEntity<Map<String, String>> payosWebhook(
            @RequestBody Map<String, Object> webhookData
    ) {
        try {
            log.info("📥 PayOS webhook nhận được: {}", webhookData);
            subscriptionService.handlePayOSWebhook(webhookData);
            return ResponseEntity.ok(Map.of("code", "00", "desc", "success"));
        } catch (Exception e) {
            log.error("Lỗi xử lý PayOS webhook", e);
            // Trả 200 để PayOS không retry liên tục
            return ResponseEntity.ok(Map.of("code", "01", "desc", e.getMessage()));
        }
    }

    /**
     * POST /api/subscription/use-slot
     * Gọi sau khi đăng tin thành công để trừ 1 lượt
     */
    @PostMapping("/use-slot")
    public ResponseEntity<ApiResponse<Void>> useSlot(Authentication authentication) {
        try {
            Long vendorId = resolveVendorId(authentication);
            subscriptionService.consumeOneSlot(vendorId);
            return ResponseEntity.ok(ApiResponse.success("Đã sử dụng 1 lượt đăng tin", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Long resolveVendorId(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("Chưa đăng nhập");
        }
        String email = String.valueOf(authentication.getPrincipal());
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản vendor"));
        return vendor.getId();
    }
}
