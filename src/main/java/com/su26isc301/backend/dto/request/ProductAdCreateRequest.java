package com.su26isc301.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductAdCreateRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Days is required")
    @Min(value = 1, message = "Days must be at least 1")
    private Integer days;

    @NotNull(message = "Total amount is required")
    @Min(value = 10000, message = "Total amount must be at least 10,000 VND")
    private BigDecimal totalAmount;
}
