package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.VendorRegisterRequest;
import com.su26isc301.backend.dto.request.VendorUpdateRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    // 1. Đăng ký mở cửa hàng mới
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Vendor>> registerVendor(@RequestBody VendorRegisterRequest request) {
        Vendor newVendor = vendorService.registerVendor(request);
        ApiResponse<Vendor> response = ApiResponse.<Vendor>builder()
                .success(true)
                .message("Đăng ký thông tin cửa hàng thành công!")
                .data(newVendor)
                .build();
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. Cập nhật thông tin cửa hàng
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Vendor>> updateVendor(
            @PathVariable("id") Long vendorId,
            @RequestBody VendorUpdateRequest request) {
        Vendor updatedVendor = vendorService.updateVendor(vendorId, request);
        ApiResponse<Vendor> response = ApiResponse.<Vendor>builder()
                .success(true)
                .message("Cập nhật thông tin cửa hàng thành công!")
                .data(updatedVendor)
                .build();
        return ResponseEntity.ok(response);
    }

    // 3. Lấy thông tin chi tiết một cửa hàng qua Vendor ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Vendor>> getVendorById(@PathVariable("id") Long vendorId) {
        Vendor vendor = vendorService.getVendorById(vendorId);
        ApiResponse<Vendor> response = ApiResponse.<Vendor>builder()
                .success(true)
                .message("Lấy thông tin cửa hàng thành công")
                .data(vendor)
                .build();
        return ResponseEntity.ok(response);
    }

    // 4. Lấy thông tin cửa hàng qua Profile ID (Dùng khi user đăng nhập muốn xem gian hàng của họ)
    @GetMapping("/profile/{profileId}")
    public ResponseEntity<ApiResponse<Vendor>> getVendorByProfileId(@PathVariable("profileId") UUID profileId) {
        Vendor vendor = vendorService.getVendorByProfileId(profileId);
        ApiResponse<Vendor> response = ApiResponse.<Vendor>builder()
                .success(true)
                .message("Lấy thông tin cửa hàng thành công")
                .data(vendor)
                .build();
        return ResponseEntity.ok(response);
    }

    // 5. Danh sách toàn bộ các cửa hàng (Dành cho Admin quản lý hoặc trang tổng hợp)
    @GetMapping
    public ResponseEntity<ApiResponse<List<Vendor>>> getAllVendors() {
        List<Vendor> vendors = vendorService.getAllVendors();
        ApiResponse<List<Vendor>> response = ApiResponse.<List<Vendor>>builder()
                .success(true)
                .message("Lấy danh sách cửa hàng thành công")
                .data(vendors)
                .build();
        return ResponseEntity.ok(response);
    }
}
