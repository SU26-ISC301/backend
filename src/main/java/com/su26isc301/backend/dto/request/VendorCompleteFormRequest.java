package com.su26isc301.backend.dto.request;

import lombok.Data;

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
    private String cccd;
    private String taxCode;
    private String ownerFullName;
    private String ownerDateOfBirth;
}
