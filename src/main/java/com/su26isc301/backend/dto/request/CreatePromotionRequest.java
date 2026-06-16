package com.su26isc301.backend.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreatePromotionRequest {
    private Long productId;
    private BigDecimal promotionAmount;
    private BigDecimal roiPerClick;
    private java.time.ZonedDateTime startDate;
    private java.time.ZonedDateTime endDate;
}
