package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.BannerCreateRequest;
import com.su26isc301.backend.dto.request.ProductAdCreateRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.BannerResponse;
import com.su26isc301.backend.dto.response.ProductAdResponse;
import com.su26isc301.backend.service.AdvertisementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vendors/ads")
@RequiredArgsConstructor
public class AdvertisementController {

    private final AdvertisementService advertisementService;

    @PostMapping("/products")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<ProductAdResponse>> createProductAd(
            Authentication authentication,
            @Valid @RequestBody ProductAdCreateRequest request) {
        String vendorProfileId = String.valueOf(authentication.getPrincipal());
        ProductAdResponse response = advertisementService.createProductAd(vendorProfileId, request);
        return ResponseEntity.ok(ApiResponse.success("Product ad registered successfully", response));
    }

    @GetMapping("/products")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<Page<ProductAdResponse>>> getMyProductAds(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String vendorProfileId = String.valueOf(authentication.getPrincipal());
        Page<ProductAdResponse> response = advertisementService.getVendorProductAds(vendorProfileId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success("Retrieved product ads successfully", response));
    }

    @PostMapping("/banners")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<BannerResponse>> createBanner(
            Authentication authentication,
            @Valid @RequestBody BannerCreateRequest request) {
        String vendorProfileId = String.valueOf(authentication.getPrincipal());
        BannerResponse response = advertisementService.createBanner(vendorProfileId, request);
        return ResponseEntity.ok(ApiResponse.success("Banner registered successfully", response));
    }

    @GetMapping("/banners")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<Page<BannerResponse>>> getMyBanners(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String vendorProfileId = String.valueOf(authentication.getPrincipal());
        Page<BannerResponse> response = advertisementService.getVendorBanners(vendorProfileId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success("Retrieved banners successfully", response));
    }
}
