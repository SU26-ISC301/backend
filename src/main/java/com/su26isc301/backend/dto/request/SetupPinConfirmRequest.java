package com.su26isc301.backend.dto.request;

import lombok.Data;

@Data
public class SetupPinConfirmRequest {
    private String pinSetupToken;
    private String newPin;
    private String confirmPin;
}
