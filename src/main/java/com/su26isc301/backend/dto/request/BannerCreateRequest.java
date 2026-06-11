package com.su26isc301.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BannerCreateRequest {

    @NotBlank(message = "Image URL is required")
    private String imageUrl;

    private String targetUrl;

    @NotBlank(message = "Position is required")
    private String position;

    @NotNull(message = "Days is required")
    @Min(value = 1, message = "Days must be at least 1")
    private Integer days;

    @NotNull(message = "Total amount is required")
    @Min(value = 10000, message = "Total amount must be at least 10,000 VND")
    private BigDecimal totalAmount;
}
