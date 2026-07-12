package com.su26isc301.backend.dto.request;

import lombok.Data;

@Data
public class VerifyChangeOtpRequest {
    private String currentPinToken;
    private String otp;
}
