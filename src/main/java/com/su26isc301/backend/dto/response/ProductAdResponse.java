package com.su26isc301.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
public class ProductAdResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Long vendorId;
    private String shopName;
    private BigDecimal bidAmount;
    private BigDecimal totalAmount;
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private String status;
    private String paymentUrl;
    private ZonedDateTime createdAt;
}
