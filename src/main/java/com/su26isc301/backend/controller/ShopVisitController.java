package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.service.ShopVisitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ShopVisitController {

    private final ShopVisitService shopVisitService;

    /**
     * API công khai để ghi nhận lượt truy cập vào cửa hàng hoặc bài viết sản phẩm
     */
    @PostMapping("/api/visits/record")
    public ResponseEntity<ApiResponse<Void>> recordVisit(
            @RequestBody Map<String, Long> payload,
            HttpServletRequest request,
            Authentication authentication
    ) {
        Long vendorId = payload.get("vendorId");
        if (vendorId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Thiếu thông tin vendorId"));
        }
        Long productId = payload.get("productId");

        // Trích xuất IP address của khách truy cập
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        } else {
            // Lấy IP đầu tiên trong danh sách X-Forwarded-For (nếu qua nhiều proxy)
            ipAddress = ipAddress.split(",")[0].trim();
        }

        // Lấy thông tin tài khoản nếu có đăng nhập
        String email = null;
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            email = authentication.getName();
        }

        try {
            shopVisitService.recordVisit(vendorId, productId, ipAddress, email);
            return ResponseEntity.ok(ApiResponse.success("Ghi nhận lượt truy cập thành công", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Không thể ghi nhận lượt truy cập: " + e.getMessage()));
        }
    }

    /**
     * API lấy số liệu thống kê lượt truy cập cho Seller
     */
    @GetMapping("/vendors/{vendorId}/visits-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVisitsStats(
            @PathVariable("vendorId") Long vendorId,
            @RequestParam(value = "range", required = false, defaultValue = "30") Integer range,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate
    ) {
        try {
            Map<String, Object> stats = shopVisitService.getVisitsStats(vendorId, range, startDate, endDate);
            return ResponseEntity.ok(ApiResponse.success("Lấy thống kê lượt truy cập thành công", stats));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Lỗi khi lấy dữ liệu thống kê: " + e.getMessage()));
        }
    }
}
