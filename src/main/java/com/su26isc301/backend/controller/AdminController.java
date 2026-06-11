package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.MarketResearchResponse;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.service.MarketResearchService;
import com.su26isc301.backend.service.ProfileService;
import com.su26isc301.backend.service.VendorService;
import com.su26isc301.backend.service.AuditLogService;
import com.su26isc301.backend.service.ProductService;
import com.su26isc301.backend.dto.response.ProductResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private ProfileService profileService;

    @Autowired
    private VendorService vendorService;

    @Autowired
    private MarketResearchService marketResearchService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private ProductService productService;

    // API: Xem danh sách toàn bộ tài khoản
    @GetMapping("/profiles")
    public ResponseEntity<ApiResponse<List<Profile>>> getAllProfiles() {
        List<Profile> profiles = profileService.getAllProfiles();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tài khoản thành công", profiles));
    }

    // API: Vô hiệu hóa hoặc kích hoạt tài khoản
    @PostMapping("/profiles/toggle-status")
    public ResponseEntity<ApiResponse<Profile>> toggleProfileStatus(@RequestParam("profileId") UUID profileId) {
        Profile updated = profileService.toggleProfileStatus(profileId);
        try {
            auditLogService.log("TOGGLE_USER_STATUS", "Cập nhật trạng thái tài khoản " + profileId + " sang: " + (Boolean.TRUE.equals(updated.getIsActive()) ? "Hoạt động" : "Bị khóa"));
        } catch (Exception e) {
            System.err.println("Lỗi ghi log TOGGLE_USER_STATUS: " + e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái tài khoản thành công", updated));
    }

    // API: Xem danh sách toàn bộ cửa hàng (vendors)
    @GetMapping("/vendors")
    public ResponseEntity<ApiResponse<List<Vendor>>> getAllVendors() {
        List<Vendor> vendors = vendorService.getAllVendors();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách cửa hàng thành công", vendors));
    }

    // API: Cây hạng mục dùng cho module Nghiên cứu thị trường
    @GetMapping("/market-research/categories")
    public ResponseEntity<ApiResponse<List<MarketResearchResponse.MarketCategoryOption>>> getMarketResearchCategories() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy cây hạng mục nghiên cứu thị trường thành công",
                marketResearchService.getCategoryTree()
        ));
    }

    // API: Lấy dữ liệu nghiên cứu thị trường cho Admin
    @GetMapping("/market-research")
    public ResponseEntity<ApiResponse<MarketResearchResponse>> getMarketResearch(
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "query", required = false) String query
    ) {
        MarketResearchResponse response = marketResearchService.getAdminMarketResearch(categoryId, source, query);
        return ResponseEntity.ok(ApiResponse.success("Lấy dữ liệu nghiên cứu thị trường thành công", response));
    }

    // API: Mô phỏng cập nhật dữ liệu thị trường trong phạm vi demo
    @PostMapping("/market-research/sync")
    public ResponseEntity<ApiResponse<MarketResearchResponse>> syncMarketResearch(
            @RequestParam(value = "categoryId", required = false) String categoryId
    ) {
        MarketResearchResponse response = marketResearchService.syncAdminMarketResearch(categoryId);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật dữ liệu nghiên cứu thị trường thành công", response));
    }

    // API: Admin phê duyệt sản phẩm
    @PostMapping("/products/{id}/approve")
    public ResponseEntity<ApiResponse<ProductResponse>> approveProduct(@PathVariable("id") Long id) {
        ProductResponse approved = productService.approveProduct(id);
        try {
            auditLogService.log("APPROVE_PRODUCT", "Phê duyệt sản phẩm thành công: " + approved.getName() + " (ID: " + id + ")");
        } catch (Exception e) {
            System.err.println("Lỗi ghi log APPROVE_PRODUCT: " + e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.success("Phê duyệt sản phẩm thành công", approved));
    }

    // API: Admin từ chối sản phẩm
    @PostMapping("/products/{id}/reject")
    public ResponseEntity<ApiResponse<ProductResponse>> rejectProduct(
            @PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.get("reason") : "";
        ProductResponse rejected = productService.rejectProduct(id, reason);
        try {
            auditLogService.log("REJECT_PRODUCT", "Từ chối sản phẩm (ID: " + id + ") với lý do: " + reason);
        } catch (Exception e) {
            System.err.println("Lỗi ghi log REJECT_PRODUCT: " + e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.success("Từ chối duyệt sản phẩm thành công", rejected));
    }

    // API: Admin gửi cảnh báo sản phẩm
    @PostMapping("/products/{id}/warn")
    public ResponseEntity<ApiResponse<ProductResponse>> warnProduct(
            @PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.get("reason") : "";
        ProductResponse warned = productService.warnProduct(id, reason);
        try {
            auditLogService.log("WARN_PRODUCT", "Gửi cảnh báo vi phạm cho sản phẩm (ID: " + id + ") với nội dung: " + reason);
        } catch (Exception e) {
            System.err.println("Lỗi ghi log WARN_PRODUCT: " + e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.success("Gửi cảnh báo sản phẩm thành công", warned));
    }
}
