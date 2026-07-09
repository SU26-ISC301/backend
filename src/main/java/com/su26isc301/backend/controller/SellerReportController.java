package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.SellerAppealRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.ReportDetailResponse;
import com.su26isc301.backend.dto.response.ReportSummaryResponse;
import com.su26isc301.backend.dto.response.SellerViolationResponse;
import com.su26isc301.backend.enums.ReportStatus;
import com.su26isc301.backend.service.ReportModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
@Tag(name = "Reports (Seller)", description = "Endpoints for sellers to view violations and submit appeals")
public class SellerReportController {

    private final ReportModerationService moderationService;

    @GetMapping("/reports")
    @Operation(summary = "Xem danh sách báo cáo vi phạm liên quan tới shop của mình")
    public ResponseEntity<ApiResponse<Page<ReportSummaryResponse>>> getSellerReports(
            Authentication authentication,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) ReportStatus status
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ReportSummaryResponse> reports = moderationService.getSellerReports(email, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách báo cáo thành công", reports));
    }

    @GetMapping("/violations")
    @Operation(summary = "Xem danh sách các vi phạm/cảnh báo đang ghi nhận cho shop")
    public ResponseEntity<ApiResponse<List<SellerViolationResponse>>> getSellerViolations(
            Authentication authentication
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        List<SellerViolationResponse> violations = moderationService.getSellerViolations(email);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách vi phạm thành công", violations));
    }

    @PostMapping("/reports/{reportId}/appeals")
    @Operation(summary = "Gửi khiếu nại (appeal) cho một báo cáo vi phạm (hạn 7 ngày)")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> submitAppeal(
            Authentication authentication,
            @PathVariable("reportId") Long reportId,
            @RequestBody SellerAppealRequest request
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        ReportDetailResponse reportDetail = moderationService.submitAppeal(email, reportId, request);
        return ResponseEntity.ok(ApiResponse.success("Gửi khiếu nại thành công", reportDetail));
    }
}
