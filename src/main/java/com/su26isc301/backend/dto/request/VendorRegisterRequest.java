package com.su26isc301.backend.dto.request;

import lombok.Data;
import java.util.UUID;

@Data
public class VendorRegisterRequest {
    private UUID profileId;
    private String shopName;
    private String description;
    private String logoUrl;
    private String email;
    private String phone;
    private String cccd;
    private String taxCode;
}
