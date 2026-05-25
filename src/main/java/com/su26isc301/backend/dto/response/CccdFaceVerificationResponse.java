package com.su26isc301.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CccdFaceVerificationResponse {
    private boolean verified;
    private String message;
    private CccdVerificationResponse cccd;
    private FaceMatchResult faceMatch;
}
