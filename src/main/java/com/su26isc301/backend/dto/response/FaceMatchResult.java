package com.su26isc301.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceMatchResult {
    private boolean success;
    private boolean match;
    private BigDecimal similarity;
    private Boolean bothImagesAreIdCards;
    private String code;
    private String message;
    private Map<String, Object> rawResponse;
}
