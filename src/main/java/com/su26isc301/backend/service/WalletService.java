package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.response.PaymentLinkResponse;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.entity.WalletTransaction;
import com.su26isc301.backend.repository.VendorRepository;
import com.su26isc301.backend.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final VendorRepository vendorRepository;
    private final WalletTransactionRepository transactionRepository;
    private final PayOSService payOSService;

    public BigDecimal getBalance(Long vendorId) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy vendor"));
        return vendor.getPromotionalBalance() != null ? vendor.getPromotionalBalance() : BigDecimal.ZERO;
    }

    @Transactional
    public PaymentLinkResponse createTopUpPaymentLink(Long vendorId, BigDecimal amount, String paymentMethod) {
        if (amount.compareTo(new BigDecimal("10000")) < 0) {
            throw new RuntimeException("Số tiền nạp tối thiểu là 10.000 VNĐ");
        }

        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy vendor"));

        long orderCode = Math.abs(System.currentTimeMillis() % 1_000_000_000L)
                + ThreadLocalRandom.current().nextLong(1, 100);

        String description = "Nap vi " + vendor.getShopName();
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        WalletTransaction transaction = WalletTransaction.builder()
                .vendor(vendor)
                .amount(amount)
                .transactionType("TOP_UP")
                .paymentRef(String.valueOf(orderCode))
                .status("PENDING")
                .build();
        transaction = transactionRepository.save(transaction);

        String paymentUrl;
        if ("payos".equalsIgnoreCase(paymentMethod) || paymentMethod == null) {
            paymentUrl = payOSService.createPaymentLink(orderCode, amount.longValue(), description);
        } else {
            paymentUrl = null;
        }

        transaction.setPaymentUrl(paymentUrl);
        transactionRepository.save(transaction);

        return PaymentLinkResponse.builder()
                .paymentUrl(paymentUrl)
                .orderCode(String.valueOf(orderCode))
                .amount(amount.longValue())
                .planType("top_up")
                .transactionId(transaction.getId())
                .build();
    }

    @Transactional
    public void handlePayOSWebhook(Map<String, Object> webhookData) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) webhookData.get("data");
        if (data == null) return;

        String orderCode = String.valueOf(data.get("orderCode"));
        String payosStatus = (String) data.get("status");

        WalletTransaction transaction = transactionRepository.findByPaymentRefWithLock(orderCode)
                .orElse(null);
        
        if (transaction == null || !"TOP_UP".equals(transaction.getTransactionType())) {
            return;
        }

        if (!"PENDING".equals(transaction.getStatus())) {
            return;
        }

        if ("PAID".equals(payosStatus)) {
            activateTopUp(transaction);
        } else if ("CANCELLED".equals(payosStatus) || "EXPIRED".equals(payosStatus)) {
            transaction.setStatus("FAILED");
            transactionRepository.save(transaction);
        }
    }

    @Transactional
    public String checkPaymentResult(String orderCode, Long vendorId) {
        WalletTransaction transaction = transactionRepository.findByPaymentRefWithLock(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch nạp tiền"));

        if (!transaction.getVendor().getId().equals(vendorId)) {
            throw new RuntimeException("Không có quyền truy cập giao dịch này");
        }

        if ("SUCCESS".equals(transaction.getStatus())) return "paid";
        if ("FAILED".equals(transaction.getStatus())) return "cancelled";

        String payosStatus = payOSService.getPaymentStatus(Long.parseLong(orderCode));
        if ("PAID".equals(payosStatus)) {
            activateTopUp(transaction);
            return "paid";
        } else if ("CANCELLED".equals(payosStatus) || "EXPIRED".equals(payosStatus)) {
            transaction.setStatus("FAILED");
            transactionRepository.save(transaction);
            return "cancelled";
        }

        return "pending";
    }

    @Transactional
    protected void activateTopUp(WalletTransaction transaction) {
        if ("SUCCESS".equals(transaction.getStatus())) return;

        transaction.setStatus("SUCCESS");
        transactionRepository.save(transaction);

        Vendor vendor = transaction.getVendor();
        BigDecimal currentBalance = vendor.getPromotionalBalance() != null ? vendor.getPromotionalBalance() : BigDecimal.ZERO;
        vendor.setPromotionalBalance(currentBalance.add(transaction.getAmount()));
        vendorRepository.save(vendor);
        log.info("✅ Nạp thành công {} VNĐ vào ví của Vendor {}", transaction.getAmount(), vendor.getId());
    }

    @Transactional
    public void deductBudget(Long vendorId, BigDecimal amount, Long promotionId) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy vendor"));
        BigDecimal currentBalance = vendor.getPromotionalBalance() != null ? vendor.getPromotionalBalance() : BigDecimal.ZERO;
        
        if (currentBalance.compareTo(amount) < 0) {
            throw new RuntimeException("Số dư ví không đủ để chạy chiến dịch");
        }
        
        vendor.setPromotionalBalance(currentBalance.subtract(amount));
        vendorRepository.save(vendor);
        
        WalletTransaction transaction = WalletTransaction.builder()
                .vendor(vendor)
                .amount(amount.negate())
                .transactionType("DEDUCTION")
                .promotionId(promotionId)
                .status("SUCCESS")
                .build();
        transactionRepository.save(transaction);
    }
    
    @Transactional
    public void refundBudget(Long vendorId, BigDecimal amount, Long promotionId) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy vendor"));
        BigDecimal currentBalance = vendor.getPromotionalBalance() != null ? vendor.getPromotionalBalance() : BigDecimal.ZERO;
        
        vendor.setPromotionalBalance(currentBalance.add(amount));
        vendorRepository.save(vendor);
        
        WalletTransaction transaction = WalletTransaction.builder()
                .vendor(vendor)
                .amount(amount)
                .transactionType("REFUND")
                .promotionId(promotionId)
                .status("SUCCESS")
                .build();
        transactionRepository.save(transaction);
    }
}
