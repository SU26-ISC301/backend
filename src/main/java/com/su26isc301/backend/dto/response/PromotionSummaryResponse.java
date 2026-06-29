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
public class PromotionSummaryResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productImage;
    private String productSlug;
    
    private BigDecimal spentAmount;
    private BigDecimal remainingBudget;
    
    private String status;
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
}
