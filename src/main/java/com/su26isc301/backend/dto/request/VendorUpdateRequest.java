package com.su26isc301.backend.dto.request;

import lombok.Data;

@Data
public class VendorUpdateRequest {
    private String shopName;
    private String description;
    private String logoUrl;
    private String email;
    private String phone;
    private String status;
    private String cccd;
    private String taxCode;
}