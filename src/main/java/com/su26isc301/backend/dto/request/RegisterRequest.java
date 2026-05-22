package com.su26isc301.backend.dto.request;

import lombok.Data;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Data
public class RegisterRequest {
    private String email;
    private String password;
    private String fullName;
    private String phone;
    private ZonedDateTime dateOfBirth;
}
