package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.FinanceReportResponse;
import com.su26isc301.backend.service.FinanceReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/finance")
@RequiredArgsConstructor
@Tag(name = "Seller Finance", description = "Vendor financial reporting API")
@SecurityRequirement(name = "bearerAuth")
public class FinanceController {

    private final FinanceReportService financeReportService;

    @GetMapping("/report")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Get detailed financial report for the vendor")
    public ResponseEntity<ApiResponse<FinanceReportResponse>> getFinanceReport(Authentication authentication) {
        String email = authentication.getName();
        FinanceReportResponse report = financeReportService.getFinanceReport(email);
        
        ApiResponse<FinanceReportResponse> response = ApiResponse.<FinanceReportResponse>builder()
                .success(true)
                .message("Lấy báo cáo tài chính thành công")
                .data(report)
                .build();
                
        return ResponseEntity.ok(response);
    }
}
