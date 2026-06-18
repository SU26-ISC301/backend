package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.BannerResponse;
import com.su26isc301.backend.service.AdvertisementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/ads")
@RequiredArgsConstructor
public class PublicAdvertisementController {

    private final AdvertisementService advertisementService;


    @GetMapping("/banners")
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getActiveBanners(
            @RequestParam String position) {
        List<BannerResponse> response = advertisementService.getActiveBanners(position);
        return ResponseEntity.ok(ApiResponse.success("Retrieved active banners successfully", response));
    }
}
