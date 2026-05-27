package com.su26isc301.backend.dto.request;

import com.su26isc301.backend.enums.VendorCategory;
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
    private VendorCategory category;
    private String cccd;
    private String taxCode;
    private String cccdFrontImageUrl;
    private String cccdBackImageUrl;
    private String faceImageUrl;
}
