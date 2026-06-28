package com.su26isc301.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionResponse {
    private Long id;
    private String transactionCode;
    private BigDecimal amount;
    private String type;
    private BigDecimal availableBefore;
    private BigDecimal availableAfter;
    private BigDecimal lockedBefore;
    private BigDecimal lockedAfter;
    private String referenceType;
    private Long referenceId;
    private String paymentTransactionId;
    private String status;
    private ZonedDateTime createdAt;
}
