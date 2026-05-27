package com.su26isc301.backend.dto.request;

import lombok.Data;

@Data
public class VendorOtpVerifyRequest {
    private String email;
    private String otp;
}
