package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.response.PaymentLinkResponse;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.entity.VendorWallet;
import com.su26isc301.backend.entity.WalletTopupOrder;
import com.su26isc301.backend.entity.WalletTransaction;
import com.su26isc301.backend.repository.VendorRepository;
import com.su26isc301.backend.repository.VendorWalletRepository;
import com.su26isc301.backend.repository.WalletTopupOrderRepository;
import com.su26isc301.backend.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final VendorRepository vendorRepository;
    private final VendorWalletRepository walletRepository;
    private final WalletTopupOrderRepository topupOrderRepository;
    private final WalletTransactionRepository transactionRepository;
    private final PayOSService payOSService;

    @Transactional
    public VendorWallet getOrCreateWallet(Long vendorId) {
        return walletRepository.findByVendorId(vendorId).orElseGet(() -> {
            Vendor vendor = vendorRepository.findById(vendorId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy vendor"));
            VendorWallet wallet = VendorWallet.builder()
                    .vendor(vendor)
                    .availableBalance(BigDecimal.ZERO)
                    .lockedBalance(BigDecimal.ZERO)
                    .totalDeposited(BigDecimal.ZERO)
                    .totalSpent(BigDecimal.ZERO)
                    .currency("VND")
                    .status("ACTIVE")
                    .build();
            return walletRepository.save(wallet);
        });
    }

    public BigDecimal getBalance(Long vendorId) {
        return getOrCreateWallet(vendorId).getAvailableBalance();
    }

    @Transactional
    public PaymentLinkResponse createTopUpPaymentLink(Long vendorId, BigDecimal amount, String paymentMethod) {
        if (amount.compareTo(new BigDecimal("10000")) < 0) {
            throw new RuntimeException("Số tiền nạp tối thiểu là 10.000 VNĐ");
        }

        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy vendor"));

        long orderCodeNumber = Math.abs(System.currentTimeMillis() % 1_000_000_000L)
                + ThreadLocalRandom.current().nextLong(1, 100);
        String orderCode = String.valueOf(orderCodeNumber);

        WalletTopupOrder order = WalletTopupOrder.builder()
                .orderCode(orderCode)
                .vendor(vendor)
                .amount(amount)
                .paymentMethod(paymentMethod != null ? paymentMethod : "payos")
                .status("PENDING_PAYMENT")
                .build();
        order = topupOrderRepository.save(order);

        String paymentUrl;
        if ("payos".equalsIgnoreCase(order.getPaymentMethod())) {
            String description = "Nap vi " + vendor.getShopName();
            if (description.length() > 25) {
                description = description.substring(0, 25);
            }
            paymentUrl = payOSService.createPaymentLink(orderCodeNumber, amount.longValue(), description);
            order.setPaymentUrl(paymentUrl);
            topupOrderRepository.save(order);
        } else {
            paymentUrl = null;
        }

        return PaymentLinkResponse.builder()
                .paymentUrl(paymentUrl)
                .orderCode(orderCode)
                .amount(amount.longValue())
                .planType("top_up")
                .transactionId(order.getId())
                .build();
    }

    @Transactional
    public void handlePayOSWebhook(Map<String, Object> webhookData) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) webhookData.get("data");
        if (data == null) return;

        String orderCode = String.valueOf(data.get("orderCode"));
        String payosStatus = (String) data.get("status");

        WalletTopupOrder order = topupOrderRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
        if (order == null || !"PENDING_PAYMENT".equals(order.getStatus())) {
            return;
        }

        if ("PAID".equals(payosStatus)) {
            activateTopUp(order);
        } else if ("CANCELLED".equals(payosStatus) || "EXPIRED".equals(payosStatus)) {
            order.setStatus("FAILED");
            topupOrderRepository.save(order);
        }
    }

    @Transactional
    public String checkPaymentResult(String orderCode, Long vendorId) {
        WalletTopupOrder order = topupOrderRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch nạp tiền"));

        if (!order.getVendor().getId().equals(vendorId)) {
            throw new RuntimeException("Không có quyền truy cập giao dịch này");
        }

        if ("SUCCESS".equals(order.getStatus())) return "paid";
        if ("FAILED".equals(order.getStatus()) || "CANCELLED".equals(order.getStatus())) return "cancelled";

        if ("payos".equalsIgnoreCase(order.getPaymentMethod())) {
            // [LOCAL TEST] Tạm tắt gọi PayOS, ép thành công luôn
            // String payosStatus = payOSService.getPaymentStatus(Long.parseLong(orderCode));
            String payosStatus = "PAID";
            if ("PAID".equals(payosStatus)) {
                activateTopUp(order);
                return "paid";
            } else if ("CANCELLED".equals(payosStatus) || "EXPIRED".equals(payosStatus)) {
                order.setStatus("FAILED");
                topupOrderRepository.save(order);
                return "cancelled";
            }
        }
        return "pending";
    }

    @Transactional
    protected void activateTopUp(WalletTopupOrder order) {
        if ("SUCCESS".equals(order.getStatus())) return;

        VendorWallet wallet = walletRepository.findByVendorIdForUpdate(order.getVendor().getId())
                .orElseGet(() -> getOrCreateWallet(order.getVendor().getId()));

        BigDecimal availableBefore = wallet.getAvailableBalance();
        BigDecimal lockedBefore = wallet.getLockedBalance();

        wallet.setAvailableBalance(wallet.getAvailableBalance().add(order.getAmount()));
        wallet.setTotalDeposited(wallet.getTotalDeposited().add(order.getAmount()));
        walletRepository.save(wallet);

        order.setStatus("SUCCESS");
        order.setPaidAt(ZonedDateTime.now());
        topupOrderRepository.save(order);

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .vendor(order.getVendor())
                .transactionCode("WLT-" + UUID.randomUUID().toString())
                .amount(order.getAmount())
                .type("TOP_UP")
                .status("SUCCESS")
                .availableBefore(availableBefore)
                .availableAfter(wallet.getAvailableBalance())
                .lockedBefore(lockedBefore)
                .lockedAfter(wallet.getLockedBalance())
                .referenceType("TOPUP_ORDER")
                .referenceId(order.getId())
                .build();
        transactionRepository.save(tx);

        log.info("✅ Nạp thành công {} VNĐ vào ví của Vendor {}", order.getAmount(), order.getVendor().getId());
    }

    // For backwards compatibility with Banner and ProductAd deductions
    @Transactional
    public void deductForProductAd(Long vendorId, BigDecimal amount, Long productAdId) {
        deductGeneric(vendorId, amount, "PRODUCT_AD", productAdId);
    }

    @Transactional
    public void deductForBanner(Long vendorId, BigDecimal amount, Long bannerId) {
        deductGeneric(vendorId, amount, "BANNER", bannerId);
    }

    @Transactional
    public void deductGeneric(Long vendorId, BigDecimal amount, String refType, Long refId) {
        VendorWallet wallet = walletRepository.findByVendorIdForUpdate(vendorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ví của vendor"));

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Số dư ví không đủ. Vui lòng nạp thêm tiền vào ví.");
        }

        BigDecimal availableBefore = wallet.getAvailableBalance();
        BigDecimal lockedBefore = wallet.getLockedBalance();

        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(amount));
        wallet.setTotalSpent(wallet.getTotalSpent().add(amount));
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .vendor(wallet.getVendor())
                .transactionCode("WLT-" + UUID.randomUUID().toString())
                .amount(amount.negate())
                .type("DEDUCTION_" + refType)
                .status("SUCCESS")
                .availableBefore(availableBefore)
                .availableAfter(wallet.getAvailableBalance())
                .lockedBefore(lockedBefore)
                .lockedAfter(wallet.getLockedBalance())
                .referenceType(refType)
                .referenceId(refId)
                .build();
        transactionRepository.save(tx);
    }

    @Transactional
    public void refundBudget(Long vendorId, BigDecimal amount, Long promotionId) {
        VendorWallet wallet = walletRepository.findByVendorIdForUpdate(vendorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ví của vendor"));

        BigDecimal availableBefore = wallet.getAvailableBalance();
        BigDecimal lockedBefore = wallet.getLockedBalance();

        wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .vendor(wallet.getVendor())
                .transactionCode("WLT-" + UUID.randomUUID().toString())
                .amount(amount)
                .type("REFUND")
                .status("SUCCESS")
                .availableBefore(availableBefore)
                .availableAfter(wallet.getAvailableBalance())
                .lockedBefore(lockedBefore)
                .lockedAfter(wallet.getLockedBalance())
                .referenceType("PROMOTION")
                .referenceId(promotionId)
                .build();
        transactionRepository.save(tx);
    }
}
