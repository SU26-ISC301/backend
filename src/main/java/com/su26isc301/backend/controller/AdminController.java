package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.service.ProfileService;
import com.su26isc301.backend.service.VendorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private ProfileService profileService;

    @Autowired
    private VendorService vendorService;

    // API: Xem danh sách toàn bộ tài khoản
    @GetMapping("/profiles")
    public ResponseEntity<ApiResponse<List<Profile>>> getAllProfiles() {
        List<Profile> profiles = profileService.getAllProfiles();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tài khoản thành công", profiles));
    }

    // API: Xem danh sách toàn bộ cửa hàng (vendors)
    @GetMapping("/vendors")
    public ResponseEntity<ApiResponse<List<Vendor>>> getAllVendors() {
        List<Vendor> vendors = vendorService.getAllVendors();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách cửa hàng thành công", vendors));
    }
}
