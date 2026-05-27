package com.su26isc301.backend.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class VendorCompleteFormRequest {
    private String email;
    private String password;
    private String confirmPassword;
    private String ownerPhone;

    private String shopName;
    private String category;
    private String shopEmail;
    private String shopPhone;
    private String taxCode;

    private MultipartFile frontImage;
    private MultipartFile backImage;
    private MultipartFile faceImage;
}
