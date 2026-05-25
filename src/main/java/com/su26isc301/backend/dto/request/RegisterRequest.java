package com.su26isc301.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Data
public class RegisterRequest {
    @NotBlank(message = "Email không được để trống")
    private String email;
    @NotBlank(message = "Password không được để trống")
    private String password;
    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;
    private String phone;
    private LocalDate dateOfBirth;

}
