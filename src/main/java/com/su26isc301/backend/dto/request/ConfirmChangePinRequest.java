package com.su26isc301.backend.dto.request;

import lombok.Data;

@Data
public class ConfirmChangePinRequest {
    private String pinChangeToken;
    private String newPin;
    private String confirmPin;
}
