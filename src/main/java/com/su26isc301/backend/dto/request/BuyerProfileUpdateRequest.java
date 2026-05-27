package com.su26isc301.backend.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class BuyerProfileUpdateRequest {
    private String fullName;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private String otp;
}
