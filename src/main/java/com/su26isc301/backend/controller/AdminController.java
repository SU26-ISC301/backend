package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.MarketResearchResponse;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.service.MarketResearchService;
import com.su26isc301.backend.service.ProfileService;
import com.su26isc301.backend.service.VendorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
