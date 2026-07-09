package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.AdminAppealDecisionRequest;
import com.su26isc301.backend.dto.request.AdminReportActionRequest;
import com.su26isc301.backend.dto.request.AdminReportStatusUpdateRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.ReportDetailResponse;
import com.su26isc301.backend.dto.response.ReportStatsResponse;
import com.su26isc301.backend.dto.response.ReportSummaryResponse;
import com.su26isc301.backend.enums.ReportPriority;
import com.su26isc301.backend.enums.ReportReasonCode;
import com.su26isc301.backend.enums.ReportStatus;
import com.su26isc301.backend.enums.ReportTargetType;
import com.su26isc301.backend.service.ReportModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Reports (Admin)", description = "Moderation endpoints for administrator use")
public class AdminReportController {

    private final ReportModerationService moderationService;

    @GetMapping("/reports")
    @Operation(summary = "Xem danh sách toàn bộ báo cáo vi phạm với bộ lọc và tìm kiếm")
    public ResponseEntity<ApiResponse<Page<ReportSummaryResponse>>> getReports(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) ReportStatus status,
            @RequestParam(value = "priority", required = false) ReportPriority priority,
            @RequestParam(value = "reasonCode", required = false) ReportReasonCode reasonCode,
            @RequestParam(value = "vendorId", required = false) Long vendorId,
            @RequestParam(value = "reporterUserId", required = false) UUID reporterUserId,
            @RequestParam(value = "targetType", required = false) ReportTargetType targetType,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime toDate
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ReportSummaryResponse> reports = moderationService.getReports(
                pageable, status, priority, reasonCode, vendorId, reporterUserId, targetType, query, fromDate, toDate
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách báo cáo thành công", reports));
    }

    @GetMapping("/reports/{id}")
    @Operation(summary = "Xem chi tiết báo cáo vi phạm đầy đủ thông tin (Admin)")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> getReportDetail(@PathVariable("id") Long id) {
        ReportDetailResponse reportDetail = moderationService.getReportDetailAdmin(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin chi tiết báo cáo thành công", reportDetail));
    }

    @PatchMapping("/reports/{id}/status")
    @Operation(summary = "Cập nhật trạng thái xử lý của báo cáo")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> updateReportStatus(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody AdminReportStatusUpdateRequest request
    ) {
        String adminEmail = String.valueOf(authentication.getPrincipal());
        ReportDetailResponse reportDetail = moderationService.updateReportStatus(id, request, adminEmail);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái báo cáo thành công", reportDetail));
    }

    @PostMapping("/reports/{id}/actions")
    @Operation(summary = "Áp dụng biện pháp xử lý đối với cửa hàng hoặc sản phẩm bị tố cáo")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> applyAction(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody AdminReportActionRequest request
    ) {
        String adminEmail = String.valueOf(authentication.getPrincipal());
        ReportDetailResponse reportDetail = moderationService.applyAction(id, request, adminEmail);
        return ResponseEntity.ok(ApiResponse.success("Áp dụng hình thức xử lý thành công", reportDetail));
    }

    @GetMapping("/reports/stats")
    @Operation(summary = "Thống kê số liệu cho dashboard Admin")
    public ResponseEntity<ApiResponse<ReportStatsResponse>> getReportStats() {
        ReportStatsResponse stats = moderationService.getReportStats();
        return ResponseEntity.ok(ApiResponse.success("Lấy thống kê báo cáo thành công", stats));
    }

    @GetMapping("/vendors/{vendorId}/report-summary")
    @Operation(summary = "Xem hồ sơ rủi ro và thống kê vi phạm của Vendor")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVendorReportSummary(@PathVariable("vendorId") Long vendorId) {
        Map<String, Object> summary = moderationService.getVendorReportSummary(vendorId);
        return ResponseEntity.ok(ApiResponse.success("Lấy tóm tắt rủi ro cửa hàng thành công", summary));
    }

    @PatchMapping("/reports/{reportId}/appeals/{appealId}")
    @Operation(summary = "Xử lý và đưa ra quyết định chấp thuận/từ chối khiếu nại của Vendor")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> reviewAppeal(
            Authentication authentication,
            @PathVariable("reportId") Long reportId,
            @PathVariable("appealId") Long appealId,
            @RequestBody AdminAppealDecisionRequest request
    ) {
        String adminEmail = String.valueOf(authentication.getPrincipal());
        ReportDetailResponse reportDetail = moderationService.reviewAppeal(reportId, appealId, request, adminEmail);
        return ResponseEntity.ok(ApiResponse.success("Đã phản hồi khiếu nại thành công", reportDetail));
    }
}
