package com.su26isc301.backend.dto.request;

import com.su26isc301.backend.enums.VendorCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class VendorOnboardingRequest {
    private String email;

    private String password;
    private String confirmPassword;
    private String ownerPhone;

    private String shopName;
    @Schema(allowableValues = {
            "Thời trang",
            "Mỹ phẩm",
            "Gia dụng",
            "Điện tử",
            "Mẹ và bé",
            "Sách",
            "Văn phòng phẩm"
    })
    private VendorCategory category;
    private String shopEmail;
    private String shopPhone;
    private String cccd;
    private String taxCode;
    private String ownerDateOfBirth;
}
