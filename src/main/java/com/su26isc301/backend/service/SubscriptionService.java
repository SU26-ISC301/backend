package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.response.PaymentLinkResponse;
import com.su26isc301.backend.dto.response.SubscriptionStatusResponse;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.entity.VendorSubscriptionPlan;
import com.su26isc301.backend.entity.VendorSubscriptionTransaction;
import com.su26isc301.backend.repository.VendorRepository;
import com.su26isc301.backend.repository.VendorSubscriptionPlanRepository;
import com.su26isc301.backend.repository.VendorSubscriptionTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    // ─── Định nghĩa gói ────────────────────────────────────────────────────────
    public static final String PLAN_FREE    = "free";
    public static final String PLAN_PLUS    = "plus";
    public static final String PLAN_PREMIUM = "premium";

    private static final int FREE_SLOTS    = 3;
    private static final int PLUS_SLOTS    = 20;
    private static final int PREMIUM_SLOTS = -1; // unlimited

    // Giá VNĐ (demo)
    private static final long PRICE_PLUS    = 10_000L;
    private static final long PRICE_PREMIUM = 20_000L;

    // Số ngày hiệu lực
    private static final int DAYS_PLUS    = 15;
    private static final int DAYS_PREMIUM = 30;

    // ─── Dependencies ──────────────────────────────────────────────────────────
    private final VendorRepository vendorRepository;
    private final VendorSubscriptionPlanRepository planRepository;
    private final VendorSubscriptionTransactionRepository transactionRepository;
    private final PayOSService payOSService;
    private final OtpService otpService;

    @Value("${app.mail.sender}")
    private String fromEmail;

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    // ─── 1. Tạo gói Free khi vendor mới đăng ký ──────────────────────────────

    @Transactional
    public VendorSubscriptionPlan getOrCreateFreePlan(Long vendorId) {
        return planRepository.findByVendorId(vendorId).orElseGet(() -> {
            Vendor vendor = vendorRepository.findById(vendorId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy vendor"));
            VendorSubscriptionPlan plan = VendorSubscriptionPlan.builder()
                    .vendor(vendor)
                    .planType(PLAN_FREE)
                    .totalSlots(FREE_SLOTS)
                    .usedSlots(0)
                    .isActive(true)
                    .build();
            return planRepository.save(plan);
        });
    }

    // ─── 2. Lấy trạng thái gói hiện tại ──────────────────────────────────────

    public SubscriptionStatusResponse getSubscriptionStatus(Long vendorId) {
        VendorSubscriptionPlan plan = planRepository.findByVendorId(vendorId)
                .orElseGet(() -> getOrCreateFreePlan(vendorId));

        boolean unlimited = plan.getTotalSlots() == -1;
        int remaining = unlimited ? -1 : Math.max(0, plan.getTotalSlots() - plan.getUsedSlots());
        boolean canPost = unlimited || remaining > 0;

        // Kiểm tra hết hạn
        if (plan.getExpiresAt() != null && ZonedDateTime.now().isAfter(plan.getExpiresAt())) {
            // Gói đã hết hạn → xuống Free
            canPost = false;
        }

        return SubscriptionStatusResponse.builder()
                .planType(plan.getPlanType())
                .totalSlots(plan.getTotalSlots())
                .usedSlots(plan.getUsedSlots())
                .remainingSlots(remaining)
                .startedAt(plan.getStartedAt())
                .expiresAt(plan.getExpiresAt())
                .isActive(Boolean.TRUE.equals(plan.getIsActive()))
                .canPost(canPost)
                .build();
    }

    // ─── 3. Kiểm tra có thể đăng tin không ───────────────────────────────────

    public boolean canPostProduct(Long vendorId) {
        VendorSubscriptionPlan plan = planRepository.findByVendorId(vendorId)
                .orElseGet(() -> getOrCreateFreePlan(vendorId));

        // Hết hạn → không được đăng
        if (plan.getExpiresAt() != null && ZonedDateTime.now().isAfter(plan.getExpiresAt())) {
            return false;
        }

        if (plan.getTotalSlots() == -1) return true; // unlimited (premium)
        return plan.getUsedSlots() < plan.getTotalSlots();
    }

    // ─── 4. Trừ 1 lượt đăng tin ──────────────────────────────────────────────

    @Transactional
    public void consumeOneSlot(Long vendorId) {
        VendorSubscriptionPlan plan = planRepository.findByVendorId(vendorId)
                .orElseGet(() -> getOrCreateFreePlan(vendorId));

        if (plan.getTotalSlots() == -1) return; // unlimited, không cần trừ

        if (plan.getUsedSlots() >= plan.getTotalSlots()) {
            throw new RuntimeException("Đã hết lượt đăng tin. Vui lòng nâng cấp gói!");
        }
        plan.setUsedSlots(plan.getUsedSlots() + 1);
        planRepository.save(plan);
    }

    // ─── 5. Tạo link thanh toán PayOS ────────────────────────────────────────

    @Transactional
    public PaymentLinkResponse createUpgradePaymentLink(Long vendorId, String planType, String paymentMethod) {
        validatePlanType(planType);

        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy vendor"));

        long amount = getPrice(planType);

        // Tạo orderCode duy nhất (số nguyên dương, max 9 chữ số theo PayOS)
        long orderCode = Math.abs(System.currentTimeMillis() % 1_000_000_000L)
                + ThreadLocalRandom.current().nextLong(1, 100);

        String description = "Goi " + planType.toUpperCase() + " ShopVN";

        // Lưu transaction trạng thái pending
        VendorSubscriptionTransaction transaction = VendorSubscriptionTransaction.builder()
                .vendor(vendor)
                .planType(planType)
                .amount(amount)
                .paymentMethod(paymentMethod != null ? paymentMethod : "payos")
                .paymentRef(String.valueOf(orderCode))
                .status("pending")
                .build();
        transaction = transactionRepository.save(transaction);

        String paymentUrl;
        if ("payos".equals(paymentMethod) || paymentMethod == null) {
            paymentUrl = payOSService.createPaymentLink(orderCode, amount, description, vendorId, planType);
        } else {
            // bank_transfer: trả URL mock/hướng dẫn
            paymentUrl = null;
        }

        // Cập nhật URL vào transaction
        transaction.setPaymentUrl(paymentUrl);
        transactionRepository.save(transaction);

        return PaymentLinkResponse.builder()
                .paymentUrl(paymentUrl)
                .orderCode(String.valueOf(orderCode))
                .amount(amount)
                .planType(planType)
                .transactionId(transaction.getId())
                .build();
    }

    // ─── 6. Xử lý callback PayOS (webhook) ───────────────────────────────────

    @Transactional
    public void handlePayOSWebhook(Map<String, Object> webhookData) {
        // Xác thực chữ ký
        String signature = (String) webhookData.get("signature");
        if (!payOSService.verifyWebhookSignature(webhookData, signature)) {
            log.warn("PayOS webhook signature không hợp lệ: {}", webhookData);
            throw new RuntimeException("Chữ ký webhook không hợp lệ");
        }

        Map<String, Object> data = castToMap(webhookData.get("data"));
        if (data == null) return;

        String orderCode = String.valueOf(data.get("orderCode"));
        String payosStatus = (String) data.get("status"); // PAID | CANCELLED | EXPIRED

        // Sử dụng Lock bi quan để chống Race Condition với Polling
        VendorSubscriptionTransaction transaction = transactionRepository.findByPaymentRefWithLock(orderCode)
                .orElse(null);
        if (transaction == null) {
            log.warn("Không tìm thấy transaction với orderCode={}", orderCode);
            return;
        }

        // Nếu giao dịch đã được xử lý thành công hoặc thất bại/hủy trước đó, bỏ qua
        if (!"pending".equals(transaction.getStatus())) {
            log.info("Transaction {} đã được xử lý trước đó với trạng thái: {}", orderCode, transaction.getStatus());
            return;
        }

        if ("PAID".equals(payosStatus)) {
            activatePlan(transaction);
        } else if ("CANCELLED".equals(payosStatus) || "EXPIRED".equals(payosStatus)) {
            transaction.setStatus("cancelled");
            transactionRepository.save(transaction);
        }
    }

    // ─── 7. Polling: FE gọi để check kết quả thanh toán ─────────────────────

    @Transactional
    public String checkPaymentResult(String orderCode, Long vendorId) {
        // Sử dụng Lock bi quan để đồng bộ luồng
        VendorSubscriptionTransaction transaction = transactionRepository.findByPaymentRefWithLock(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch"));

        // Đảm bảo transaction thuộc về vendor này
        if (!transaction.getVendor().getId().equals(vendorId)) {
            throw new RuntimeException("Không có quyền truy cập giao dịch này");
        }

        // Nếu đã được thanh toán hoặc bị hủy, trả kết quả luôn mà không cần gọi API PayOS
        if ("paid".equals(transaction.getStatus())) return "paid";
        if ("cancelled".equals(transaction.getStatus())) return "cancelled";

        // Nếu đang pending → hỏi PayOS
        String payosStatus = payOSService.getPaymentStatus(Long.parseLong(orderCode));
        if ("PAID".equals(payosStatus)) {
            activatePlan(transaction);
            return "paid";
        } else if ("CANCELLED".equals(payosStatus) || "EXPIRED".equals(payosStatus)) {
            transaction.setStatus("cancelled");
            transactionRepository.save(transaction);
            return "cancelled";
        }

        return "pending";
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    @Transactional
    protected void activatePlan(VendorSubscriptionTransaction transaction) {
        if ("paid".equals(transaction.getStatus())) return; // idempotent

        transaction.setStatus("paid");
        transaction.setPaidAt(ZonedDateTime.now());
        transactionRepository.save(transaction);

        // Cập nhật gói subscription
        Long vendorId = transaction.getVendor().getId();
        String planType = transaction.getPlanType();

        VendorSubscriptionPlan plan = planRepository.findByVendorId(vendorId)
                .orElseGet(() -> VendorSubscriptionPlan.builder()
                        .vendor(transaction.getVendor())
                        .build());

        plan.setPlanType(planType);
        plan.setTotalSlots(getTotalSlots(planType));
        plan.setUsedSlots(0); // Reset khi nâng cấp
        plan.setStartedAt(ZonedDateTime.now());
        plan.setExpiresAt(ZonedDateTime.now().plusDays(getExpiryDays(planType)));
        plan.setIsActive(true);
        planRepository.save(plan);

        log.info("✅ Vendor {} đã kích hoạt gói {} thành công", vendorId, planType);

        // Phân giải Email và Tên shop khi Session Hibernate còn đang mở trong Transaction
        String toEmail = transaction.getVendor().getEmail();
        if (toEmail == null || toEmail.trim().isEmpty()) {
            if (transaction.getVendor().getProfile() != null) {
                toEmail = transaction.getVendor().getProfile().getEmail();
            }
        }
        String shopName = transaction.getVendor().getShopName();

        // Gửi email xác nhận thông qua OtpService bean để kích hoạt Async chính xác
        if (toEmail != null && !toEmail.trim().isEmpty()) {
            try {
                otpService.sendSubscriptionConfirmationEmail(
                        toEmail,
                        shopName != null ? shopName : "Vendor",
                        planType,
                        transaction.getAmount(),
                        getExpiryDays(planType)
                );
            } catch (Exception mailEx) {
                System.err.println("Lỗi gọi gửi email xác nhận subscription: " + mailEx.getMessage());
            }
        }
    }

    private void validatePlanType(String planType) {
        if (!PLAN_PLUS.equals(planType) && !PLAN_PREMIUM.equals(planType)) {
            throw new RuntimeException("Gói không hợp lệ: " + planType + ". Chỉ chấp nhận 'plus' hoặc 'premium'");
        }
    }

    private long getPrice(String planType) {
        return PLAN_PLUS.equals(planType) ? PRICE_PLUS : PRICE_PREMIUM;
    }

    private int getTotalSlots(String planType) {
        return switch (planType) {
            case PLAN_PLUS    -> PLUS_SLOTS;
            case PLAN_PREMIUM -> PREMIUM_SLOTS;
            default           -> FREE_SLOTS;
        };
    }

    private int getExpiryDays(String planType) {
        return PLAN_PLUS.equals(planType) ? DAYS_PLUS : DAYS_PREMIUM;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}
