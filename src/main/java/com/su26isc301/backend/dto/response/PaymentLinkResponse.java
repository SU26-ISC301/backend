package com.su26isc301.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentLinkResponse {
    private String paymentUrl;      // Link QR / redirect PayOS
    private String orderCode;       // Mã đơn nội bộ (để polling)
    private Long amount;            // Số tiền VNĐ
    private String planType;        // 'plus' | 'premium'
    private Long transactionId;     // ID trong DB
}
