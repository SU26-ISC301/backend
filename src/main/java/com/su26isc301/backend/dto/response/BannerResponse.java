package com.su26isc301.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
public class BannerResponse {
    private Long id;
    private Long vendorId;
    private String shopName;
    private String imageUrl;
    private String targetUrl;
    private String position;
    private BigDecimal pricePaid;
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private String status;
    private String paymentUrl;
    private ZonedDateTime createdAt;
}
