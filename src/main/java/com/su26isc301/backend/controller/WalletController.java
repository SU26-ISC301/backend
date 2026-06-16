package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.WalletTopUpRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.PaymentLinkResponse;
import com.su26isc301.backend.dto.response.WalletBalanceResponse;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.repository.VendorRepository;
import com.su26isc301.backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/seller/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final VendorRepository vendorRepository;

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getBalance(Authentication authentication) {
        Long vendorId = resolveVendorId(authentication);
        com.su26isc301.backend.entity.VendorWallet wallet = walletService.getOrCreateWallet(vendorId);
        WalletBalanceResponse response = WalletBalanceResponse.builder()
                .vendorId(wallet.getVendor().getId())
                .currency(wallet.getCurrency())
                .availableBalance(wallet.getAvailableBalance())
                .lockedBalance(wallet.getLockedBalance())
                .totalDeposited(wallet.getTotalDeposited())
                .totalSpent(wallet.getTotalSpent())
                .build();
        return ResponseEntity.ok(ApiResponse.success("Lấy số dư thành công", response));
    }

    @PostMapping("/top-up")
    public ResponseEntity<ApiResponse<PaymentLinkResponse>> topUp(
            Authentication authentication,
            @RequestBody WalletTopUpRequest request
    ) {
        try {
            Long vendorId = resolveVendorId(authentication);
            PaymentLinkResponse link = walletService.createTopUpPaymentLink(
                    vendorId, request.getAmount(), request.getPaymentMethod()
            );
            return ResponseEntity.ok(ApiResponse.success("Tạo link nạp tiền thành công", link));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/check-payment")
    public ResponseEntity<ApiResponse<Map<String, String>>> checkPayment(
            Authentication authentication,
            @RequestParam String orderCode
    ) {
        Long vendorId = resolveVendorId(authentication);
        String status = walletService.checkPaymentResult(orderCode, vendorId);
        return ResponseEntity.ok(ApiResponse.success("Kiểm tra thanh toán", Map.of("status", status)));
    }

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
