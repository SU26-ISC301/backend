package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.request.VendorRegisterRequest;
import com.su26isc301.backend.dto.request.VendorUpdateRequest;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.enums.Roles;
import com.su26isc301.backend.repository.ProfileRepository;
import com.su26isc301.backend.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VendorService {

    private final VendorRepository vendorRepository;
    private final ProfileRepository profileRepository;

    @Transactional
    public Vendor registerVendor(VendorRegisterRequest request) {
        Profile profile = profileRepository.findById(request.getProfileId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin tài khoản"));

        if (vendorRepository.findByProfile(profile).isPresent()) {
            throw new RuntimeException("Tài khoản này đã đăng ký cửa hàng kinh doanh trước đó!");
        }
        profile.setRole(Roles.vendor);
        profileRepository.save(profile);

        Vendor vendor = Vendor.builder()
                .profile(profile)
                .shopName(request.getShopName())
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .email(request.getEmail())
                .phone(request.getPhone())
                .cccd(request.getCccd())
                .taxCode(request.getTaxCode())
                .build();

        return vendorRepository.save(vendor);
    }

    @Transactional
    public Vendor updateVendor(Long vendorId, VendorUpdateRequest request) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng với ID: " + vendorId));

        vendor.setShopName(request.getShopName());
        vendor.setDescription(request.getDescription());
        vendor.setLogoUrl(request.getLogoUrl());
        vendor.setEmail(request.getEmail());
        vendor.setPhone(request.getPhone());
        vendor.setStatus(request.getStatus());
        vendor.setCccd(request.getCccd());
        vendor.setTaxCode(request.getTaxCode());

        return vendorRepository.save(vendor);
    }

    public Vendor getVendorById(Long vendorId) {
        return vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng với ID: " + vendorId));
    }

    public Vendor getVendorByProfileId(UUID profileId) {
        return vendorRepository.findByProfileId(profileId)
                .orElseThrow(() -> new RuntimeException("Tài khoản này hiện chưa đăng ký cửa hàng"));
    }

    public List<Vendor> getAllVendors() {
        return vendorRepository.findAll();
    }
}
