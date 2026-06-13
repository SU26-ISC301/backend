package com.su26isc301.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class WalletBalanceResponse {
    private BigDecimal balance;
}
