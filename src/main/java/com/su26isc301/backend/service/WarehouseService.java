package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.request.WarehouseRequest;
import com.su26isc301.backend.dto.response.WarehouseResponse;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.entity.Warehouse;
import com.su26isc301.backend.exception.BadRequestException;
import com.su26isc301.backend.exception.ForbiddenAccessException;
import com.su26isc301.backend.exception.ResourceNotFoundException;
import com.su26isc301.backend.repository.ProfileRepository;
import com.su26isc301.backend.repository.VendorRepository;
import com.su26isc301.backend.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final ProfileRepository profileRepository;
    private final VendorRepository vendorRepository;

    @Transactional(readOnly = true)
    public WarehouseResponse getVendorWarehouse(String email) {
        Vendor vendor = getVendorByEmail(email);
        return warehouseRepository.findByVendor(vendor)
                .map(this::mapToResponse)
                .orElse(null); // Return null if they don't have a warehouse yet
    }

    @Transactional
    public WarehouseResponse createWarehouse(String email, WarehouseRequest request) {
        Vendor vendor = getVendorByEmail(email);

        Optional<Warehouse> existing = warehouseRepository.findByVendor(vendor);
        if (existing.isPresent()) {
            throw new BadRequestException("Mỗi gian hàng chỉ được phép có tối đa 1 kho hàng.");
        }

        validateWarehouseRequest(request);

        Warehouse warehouse = Warehouse.builder()
                .vendor(vendor)
                .type("PICKUP") // Mặc định là kho lấy hàng
                .warehouseName(request.getWarehouseName())
                .contactName(request.getContactName())
                .phoneNumber(request.getPhoneNumber())
                .province(request.getProvince())
                .district(request.getDistrict())
                .ward(request.getWard())
                .addressDetail(request.getAddressDetail())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .isDefault(true) // Là kho duy nhất nên luôn là mặc định
                .status("ACTIVE")
                .shippingRegions("[]") // Bỏ qua thiết lập khu vực vì chỉ có 1 kho
                .build();

        return mapToResponse(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse updateWarehouse(String email, Long id, WarehouseRequest request) {
        Vendor vendor = getVendorByEmail(email);

        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho hàng với ID: " + id));

        if (!warehouse.getVendor().getId().equals(vendor.getId())) {
            throw new ForbiddenAccessException("Bạn không có quyền chỉnh sửa kho hàng này.");
        }

        validateWarehouseRequest(request);

        warehouse.setWarehouseName(request.getWarehouseName());
        warehouse.setContactName(request.getContactName());
        warehouse.setPhoneNumber(request.getPhoneNumber());
        warehouse.setProvince(request.getProvince());
        warehouse.setDistrict(request.getDistrict());
        warehouse.setWard(request.getWard());
        warehouse.setAddressDetail(request.getAddressDetail());
        warehouse.setLatitude(request.getLatitude());
        warehouse.setLongitude(request.getLongitude());

        return mapToResponse(warehouseRepository.save(warehouse));
    }

    private Vendor getVendorByEmail(String email) {
        Profile profile = profileRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản"));
        return vendorRepository.findByProfile(profile)
                .orElseThrow(() -> new ForbiddenAccessException("Tài khoản chưa đăng ký gian hàng Vendor"));
    }

    private void validateWarehouseRequest(WarehouseRequest request) {
        if (request.getWarehouseName() == null || request.getWarehouseName().trim().isEmpty()) {
            throw new BadRequestException("Tên kho hàng không được để trống.");
        }
        if (request.getWarehouseName().length() > 50) {
            throw new BadRequestException("Tên kho hàng không được vượt quá 50 ký tự.");
        }
        // Thêm các validation khác nếu cần
    }

    private WarehouseResponse mapToResponse(Warehouse warehouse) {
        WarehouseResponse response = new WarehouseResponse();
        response.setId(warehouse.getId());
        response.setType(warehouse.getType());
        response.setWarehouseName(warehouse.getWarehouseName());
        response.setContactName(warehouse.getContactName());
        response.setPhoneNumber(warehouse.getPhoneNumber());
        response.setProvince(warehouse.getProvince());
        response.setDistrict(warehouse.getDistrict());
        response.setWard(warehouse.getWard());
        response.setAddressDetail(warehouse.getAddressDetail());
        response.setLatitude(warehouse.getLatitude());
        response.setLongitude(warehouse.getLongitude());
        response.setIsDefault(warehouse.getIsDefault());
        response.setStatus(warehouse.getStatus());
        response.setCreatedAt(warehouse.getCreatedAt());
        return response;
    }
}
