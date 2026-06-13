package com.su26isc301.backend.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreatePromotionRequest {
    private Long productId;
    private Integer days;
    private BigDecimal budget;
}
