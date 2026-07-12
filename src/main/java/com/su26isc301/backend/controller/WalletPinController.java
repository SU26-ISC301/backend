package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.*;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.WalletPinStatusResponse;
import com.su26isc301.backend.service.WalletPinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/seller/wallet/pin")
@RequiredArgsConstructor
@Tag(name = "Wallet PIN (Seller)", description = "APIs for sellers to setup and change their wallet PIN")
public class WalletPinController {

    private final WalletPinService walletPinService;

    @GetMapping("/status")
    @Operation(summary = "Kiểm tra xem ví của người bán đã kích hoạt mã PIN chưa")
    public ResponseEntity<ApiResponse<WalletPinStatusResponse>> getPinStatus(Authentication authentication) {
        String email = String.valueOf(authentication.getPrincipal());
        WalletPinStatusResponse status = walletPinService.getPinStatus(email);
        return ResponseEntity.ok(ApiResponse.success("Lấy trạng thái mã PIN thành công", status));
    }

    @PostMapping("/setup/request-otp")
    @Operation(summary = "Yêu cầu gửi OTP về email để kích hoạt mã PIN lần đầu")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestSetupOtp(Authentication authentication) {
        String email = String.valueOf(authentication.getPrincipal());
        walletPinService.requestSetupOtp(email);
        return ResponseEntity.ok(ApiResponse.success(
                "OTP đã được gửi về email của bạn",
                Map.of("message", "OTP đã được gửi về email của bạn", "expiresInSeconds", 300)
        ));
    }

    @PostMapping("/setup/verify-otp")
    @Operation(summary = "Xác thực OTP gửi về email để chuẩn bị tạo mã PIN")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifySetupOtp(
            Authentication authentication,
            @RequestBody Map<String, String> body
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        String otp = body.get("otp");
        if (otp == null || otp.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng nhập mã OTP"));
        }
        String setupToken = walletPinService.verifySetupOtp(email, otp);
        return ResponseEntity.ok(ApiResponse.success(
                "Xác thực OTP thành công",
                Map.of("pinSetupToken", setupToken, "expiresInSeconds", 600)
        ));
    }

    @PostMapping("/setup/confirm")
    @Operation(summary = "Xác nhận thiết lập mã PIN ví lần đầu")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmSetupPin(
            Authentication authentication,
            @RequestBody SetupPinConfirmRequest request
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        walletPinService.confirmSetupPin(email, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Kích hoạt mã PIN ví thành công",
                Map.of("enabled", true, "message", "Đã kích hoạt mã PIN ví")
        ));
    }

    @PostMapping("/change/verify-current")
    @Operation(summary = "Xác thực mã PIN ví hiện tại khi đổi PIN")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyCurrentPin(
            Authentication authentication,
            @RequestBody VerifyCurrentPinRequest request
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        String currentToken = walletPinService.verifyCurrentPin(email, request.getCurrentPin());
        return ResponseEntity.ok(ApiResponse.success(
                "Xác thực mã PIN hiện tại thành công",
                Map.of("currentPinToken", currentToken, "expiresInSeconds", 600)
        ));
    }

    @PostMapping("/change/request-otp")
    @Operation(summary = "Yêu cầu gửi OTP về email khi đổi mã PIN")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestChangeOtp(
            Authentication authentication,
            @RequestBody ChangePinRequest request
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        walletPinService.requestChangeOtp(email, request.getCurrentPinToken());
        return ResponseEntity.ok(ApiResponse.success(
                "OTP đã được gửi về email của bạn",
                Map.of("message", "OTP đã được gửi về email của bạn", "expiresInSeconds", 300)
        ));
    }

    @PostMapping("/change/verify-otp")
    @Operation(summary = "Xác thực OTP gửi về email khi đổi mã PIN")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyChangeOtp(
            Authentication authentication,
            @RequestBody VerifyChangeOtpRequest request
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        String pinChangeToken = walletPinService.verifyChangeOtp(email, request.getCurrentPinToken(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success(
                "Xác thực OTP thành công",
                Map.of("pinChangeToken", pinChangeToken, "expiresInSeconds", 600)
        ));
    }

    @PostMapping("/change/confirm")
    @Operation(summary = "Xác nhận đổi mã PIN ví mới")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmChangePin(
            Authentication authentication,
            @RequestBody ConfirmChangePinRequest request
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        walletPinService.confirmChangePin(email, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Đổi mã PIN ví thành công",
                Map.of("enabled", true, "message", "Đã thay đổi mã PIN ví")
        ));
    }
}
