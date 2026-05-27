package com.su26isc301.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class VendorLoginResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private Long vendorId;
    private UUID profileId;
    private String shopName;
    private String status;
}
