package com.su26isc301.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class WalletBalanceResponse {
    private Long vendorId;
    private String currency;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
    private BigDecimal totalDeposited;
    private BigDecimal totalSpent;
}
