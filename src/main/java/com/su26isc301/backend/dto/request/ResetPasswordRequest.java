package com.su26isc301.backend.dto.request;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String email;
    private String password;
    private String confirmPassword;
}
