package com.su26isc301.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class VendorOtpVerifyResponse {
    private boolean existingBuyer;
    private boolean requiresPassword;
    private boolean ownerPhoneLocked;
    private boolean alreadyRegisteredVendor;
    private UUID profileId;
    private String email;
    private String ownerPhone;
}
