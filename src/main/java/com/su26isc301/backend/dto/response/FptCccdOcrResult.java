package com.su26isc301.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FptCccdOcrResult {
    private boolean success;
    private Integer errorCode;
    private String errorMessage;
    private String cardNumber;
    private String cardType;
    private String side;
    private Map<String, Object> extractedData;
    private Map<String, Object> rawResponse;
}
