package com.su26isc301.backend.dto.request;

import com.su26isc301.backend.enums.VendorCategory;
import lombok.Data;

@Data
public class VendorUpdateRequest {
    private String shopName;
    private String description;
    private String logoUrl;
    private String email;
    private String phone;
    private VendorCategory category;
    private String status;
    private String cccd;
    private String taxCode;
    private String cccdFrontImageUrl;
    private String cccdBackImageUrl;
    private String faceImageUrl;
}
