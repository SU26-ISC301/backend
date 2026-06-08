package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.WarehouseRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.WarehouseResponse;
import com.su26isc301.backend.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping("/my-warehouse")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getMyWarehouse(Authentication authentication) {
        String email = authentication.getName();
        WarehouseResponse response = warehouseService.getVendorWarehouse(email);
        if (response == null) {
            return ResponseEntity.ok(ApiResponse.success("Bạn chưa tạo kho hàng nào.", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin kho hàng thành công", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WarehouseResponse>> createWarehouse(
            Authentication authentication,
            @RequestBody WarehouseRequest request
    ) {
        String email = authentication.getName();
        WarehouseResponse response = warehouseService.createWarehouse(email, request);
        return ResponseEntity.ok(ApiResponse.success("Tạo kho hàng thành công", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> updateWarehouse(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody WarehouseRequest request
    ) {
        String email = authentication.getName();
        WarehouseResponse response = warehouseService.updateWarehouse(email, id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật kho hàng thành công", response));
    }
}
