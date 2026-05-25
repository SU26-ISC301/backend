package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.CccdFaceVerificationResponse;
import com.su26isc301.backend.dto.response.CccdVerificationResponse;
import com.su26isc301.backend.service.FptCccdVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
@Tag(name = "Identity Verification", description = "CCCD/CMND verification APIs")
public class IdentityVerificationController {

    private final FptCccdVerificationService fptCccdVerificationService;

    @PostMapping(value = "/cccd/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Xác thực CCCD/CMND bằng OCR FPT.AI với ảnh mặt trước và mặt sau")
    public ResponseEntity<ApiResponse<CccdVerificationResponse>> verifyCccd(
            @RequestPart("frontImage") MultipartFile frontImage,
            @RequestPart("backImage") MultipartFile backImage
    ) {
        CccdVerificationResponse result = fptCccdVerificationService.verify(frontImage, backImage);
        String message = result.isVerified()
                ? "Xác thực CCCD thành công"
                : "Xác thực CCCD chưa đạt, vui lòng kiểm tra lại ảnh";

        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @PostMapping(value = "/cccd/verify-with-face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Xác thực CCCD/CMND và so khớp khuôn mặt selfie với ảnh trên CCCD")
    public ResponseEntity<ApiResponse<CccdFaceVerificationResponse>> verifyCccdWithFace(
            @RequestPart("frontImage") MultipartFile frontImage,
            @RequestPart("backImage") MultipartFile backImage,
            @RequestPart("faceImage") MultipartFile faceImage
    ) {
        CccdFaceVerificationResponse result = fptCccdVerificationService.verifyWithFace(
                frontImage,
                backImage,
                faceImage
        );
        String message = result.isVerified()
                ? "Xác thực CCCD và khuôn mặt thành công"
                : "Xác thực chưa đạt, vui lòng kiểm tra lại ảnh";

        return ResponseEntity.ok(ApiResponse.success(message, result));
    }
}
