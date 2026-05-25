package com.su26isc301.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CccdVerificationResponse {
    private boolean verified;
    private String message;
    private String cccdNumber;
    private boolean frontSideValid;
    private boolean backSideValid;
    private FptCccdOcrResult front;
    private FptCccdOcrResult back;
}
