package com.su26isc301.backend.dto.request;

import lombok.Data;

@Data
public class LoginRequest {
    private String identifier;
    private String password;
}
