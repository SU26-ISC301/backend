package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.CreatePromotionRequest;
import com.su26isc301.backend.dto.request.ClickPromotionRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.entity.PostPromotion;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.repository.ProfileRepository;
import com.su26isc301.backend.repository.VendorRepository;
import com.su26isc301.backend.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;
    private final VendorRepository vendorRepository;
    private final ProfileRepository profileRepository;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Long>> createPromotion(
            Authentication authentication,
            @RequestBody CreatePromotionRequest request) {
        try {
            Long vendorId = resolveVendorId(authentication);
            PostPromotion promo = promotionService.createPromotion(
                    vendorId, request.getProductId(), request.getPromotionAmount(), request.getRoiPerClick(), request.getStartDate(), request.getEndDate());
            return ResponseEntity.ok(ApiResponse.success("Tạo chiến dịch quảng cáo thành công", promo.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/impression")
    public ResponseEntity<Void> recordImpression(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody ClickPromotionRequest request) {
        java.util.UUID viewerId = resolveViewerId(authentication);
        promotionService.recordImpression(id, request.getSessionId(), viewerId, request.getSurface());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/click")
    public ResponseEntity<Void> recordClick(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody ClickPromotionRequest request) {
        java.util.UUID viewerId = resolveViewerId(authentication);
        promotionService.recordClick(id, request.getSessionId(), viewerId, request.getSurface());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<ApiResponse<PostPromotion>> stopPromotion(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody java.util.Map<String, Object> request) {
        try {
            Long vendorId = resolveVendorId(authentication);
            boolean confirm = request.containsKey("confirm") && (boolean) request.get("confirm");
            String reason = request.containsKey("reason") ? request.get("reason").toString() : null;
            PostPromotion promo = promotionService.stopPromotion(id, vendorId, confirm, reason);
            return ResponseEntity.ok(ApiResponse.success("Dừng chiến dịch thành công", promo));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private Long resolveVendorId(Authentication authentication) {
        if (authentication == null) throw new RuntimeException("Chưa đăng nhập");
        String email = String.valueOf(authentication.getPrincipal());
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản vendor"));
        return vendor.getId();
    }

    private java.util.UUID resolveViewerId(Authentication authentication) {
        if (authentication == null) return null;
        String email = String.valueOf(authentication.getPrincipal());
        return profileRepository.findByEmail(email).map(Profile::getId).orElse(null);
    }
}
