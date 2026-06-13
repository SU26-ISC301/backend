package com.su26isc301.backend.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class WalletTopUpRequest {
    private BigDecimal amount;
    private String paymentMethod;
}
