package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.CreateReportRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.ReportDetailResponse;
import com.su26isc301.backend.dto.response.ReportSummaryResponse;
import com.su26isc301.backend.enums.ReportReasonCode;
import com.su26isc301.backend.enums.ReportTargetType;
import com.su26isc301.backend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports (Customer)", description = "APIs for customers to submit and view reports")
public class ReportController {

    private final ReportService reportService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tạo báo cáo mới kèm tệp bằng chứng trực tiếp")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> createReportMultipart(
            Authentication authentication,
            @RequestParam("targetType") ReportTargetType targetType,
            @RequestParam("targetId") Long targetId,
            @RequestParam("reasonCode") ReportReasonCode reasonCode,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "confirmTruthful") Boolean confirmTruthful,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        CreateReportRequest request = CreateReportRequest.builder()
                .targetType(targetType)
                .targetId(targetId)
                .reasonCode(reasonCode)
                .description(description)
                .confirmTruthful(confirmTruthful)
                .build();
        ReportDetailResponse response = reportService.createReport(email, request, files);
        return ResponseEntity.ok(ApiResponse.success("Gửi báo cáo thành công", response));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Tạo báo cáo mới bằng JSON (dùng URL bằng chứng đã upload sẵn)")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> createReportJson(
            Authentication authentication,
            @RequestBody CreateReportRequest request
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        ReportDetailResponse response = reportService.createReport(email, request, null);
        return ResponseEntity.ok(ApiResponse.success("Gửi báo cáo thành công", response));
    }

    @GetMapping("/my")
    @Operation(summary = "Lấy danh sách báo cáo do chính mình tạo")
    public ResponseEntity<ApiResponse<Page<ReportSummaryResponse>>> getMyReports(
            Authentication authentication,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ReportSummaryResponse> reports = reportService.getMyReports(email, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách báo cáo thành công", reports));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết báo cáo cụ thể")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> getReportDetail(
            Authentication authentication,
            @PathVariable("id") Long id
    ) {
        String email = String.valueOf(authentication.getPrincipal());
        ReportDetailResponse reportDetail = reportService.getReportDetail(email, id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin chi tiết báo cáo thành công", reportDetail));
    }

    @GetMapping("/reasons")
    @Operation(summary = "Lấy danh sách lý do báo cáo để hiển thị lên form")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getReasons() {
        List<Map<String, Object>> reasons = Arrays.stream(ReportReasonCode.values())
                .map(r -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("reasonCode", r.name());
                    map.put("label", r.getLabel());
                    map.put("defaultPriority", r.getDefaultPriority().name());
                    return map;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lý do báo cáo thành công", reasons));
    }
}
