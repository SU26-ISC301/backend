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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WalletServiceTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private VendorWalletRepository walletRepository;

    @Mock
    private WalletTopupOrderRepository topupOrderRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private PayOSService payOSService;

    @InjectMocks
    private WalletService walletService;

    private Vendor vendor;
    private VendorWallet wallet;

    @BeforeEach
    void setUp() {
        vendor = new Vendor();
        vendor.setId(1L);
        vendor.setShopName("Test Shop");

        wallet = VendorWallet.builder()
                .id(1L)
                .vendor(vendor)
                .availableBalance(new BigDecimal("150000"))
                .lockedBalance(BigDecimal.ZERO)
                .totalDeposited(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .currency("VND")
                .status("ACTIVE")
                .build();
    }

    @Test
    void createTopUpPaymentLink_doesNotChangeBalance() {
        when(vendorRepository.findById(1L)).thenReturn(Optional.of(vendor));
        when(topupOrderRepository.save(any(WalletTopupOrder.class))).thenAnswer(invocation -> {
            WalletTopupOrder o = invocation.getArgument(0);
            o.setId(10L);
            return o;
        });
        when(payOSService.createPaymentLink(anyLong(), anyLong(), anyString())).thenReturn("http://payos.checkout/url");

        BigDecimal amount = new BigDecimal("10000");
        PaymentLinkResponse response = walletService.createTopUpPaymentLink(1L, amount, "payos");

        assertNotNull(response);
        assertEquals("http://payos.checkout/url", response.getPaymentUrl());
        assertEquals(amount.longValue(), response.getAmount());

        // Kỳ vọng: sẵn có khả dụng (available balance) không hề thay đổi, vẫn là 150000 VND
        assertEquals(new BigDecimal("150000"), wallet.getAvailableBalance());
        verify(topupOrderRepository, times(2)).save(any(WalletTopupOrder.class));
    }

    @Test
    void checkPaymentResult_unpaid_doesNotChangeBalance() {
        WalletTopupOrder order = WalletTopupOrder.builder()
                .id(10L)
                .orderCode("12345678")
                .vendor(vendor)
                .amount(new BigDecimal("10000"))
                .paymentMethod("payos")
                .status("PENDING_PAYMENT")
                .build();

        when(topupOrderRepository.findByOrderCodeForUpdate("12345678")).thenReturn(Optional.of(order));
        when(payOSService.getPaymentStatus(12345678L)).thenReturn("PENDING");

        String status = walletService.checkPaymentResult("12345678", 1L);

        assertEquals("pending", status);
        // Số dư ví không đổi
        assertEquals(new BigDecimal("150000"), wallet.getAvailableBalance());
        verify(walletRepository, never()).save(any(VendorWallet.class));
    }

    @Test
    void checkPaymentResult_paid_creditsBalanceCorrectly() {
        WalletTopupOrder order = WalletTopupOrder.builder()
                .id(10L)
                .orderCode("12345678")
                .vendor(vendor)
                .amount(new BigDecimal("10000"))
                .paymentMethod("payos")
                .status("PENDING_PAYMENT")
                .build();

        when(topupOrderRepository.findByOrderCodeForUpdate("12345678")).thenReturn(Optional.of(order));
        when(payOSService.getPaymentStatus(12345678L)).thenReturn("PAID");
        when(walletRepository.findByVendorIdForUpdate(1L)).thenReturn(Optional.of(wallet));

        String status = walletService.checkPaymentResult("12345678", 1L);

        assertEquals("paid", status);
        // Ví được cộng tiền đúng 10.000 -> khả dụng thành 160.000 VND
        assertEquals(new BigDecimal("160000"), wallet.getAvailableBalance());
        assertEquals(new BigDecimal("10000"), wallet.getTotalDeposited());
        assertEquals("SUCCESS", order.getStatus());
        verify(walletRepository, times(1)).save(wallet);
        verify(topupOrderRepository, times(1)).save(order);
        verify(transactionRepository, times(1)).save(any(WalletTransaction.class));
    }

    @Test
    void checkPaymentResult_paid_multipleCallsDoesNotDoubleCredit() {
        // Giả lập giao dịch ban đầu đã nạp thành công và có trạng thái là SUCCESS
        WalletTopupOrder order = WalletTopupOrder.builder()
                .id(10L)
                .orderCode("12345678")
                .vendor(vendor)
                .amount(new BigDecimal("10000"))
                .paymentMethod("payos")
                .status("SUCCESS")
                .build();

        when(topupOrderRepository.findByOrderCodeForUpdate("12345678")).thenReturn(Optional.of(order));

        // Gọi lần 1
        String status = walletService.checkPaymentResult("12345678", 1L);
        assertEquals("paid", status);

        // Gọi lần 2
        status = walletService.checkPaymentResult("12345678", 1L);
        assertEquals("paid", status);

        // Kỳ vọng: số dư ví không đổi khi gọi kiểm tra nhiều lần (vẫn là 150000 ban đầu)
        assertEquals(new BigDecimal("150000"), wallet.getAvailableBalance());
        verify(walletRepository, never()).save(any(VendorWallet.class));
        verify(transactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void balanceEndpoint_doesNotCountPendingOrders() {
        when(walletRepository.findByVendorId(1L)).thenReturn(Optional.of(wallet));

        // Lấy số dư ví qua phương thức getBalance
        BigDecimal balance = walletService.getBalance(1L);

        // Kỳ vọng: chỉ hiển thị số dư khả dụng thật, không cộng các lệnh nạp PENDING
        assertEquals(new BigDecimal("150000"), balance);
    }
}
