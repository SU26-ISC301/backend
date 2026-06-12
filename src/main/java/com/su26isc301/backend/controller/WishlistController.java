package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.ProductResponse;
import com.su26isc301.backend.dto.response.WishlistStatusResponse;
import com.su26isc301.backend.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wishlists")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getFavorites(Authentication authentication) {
        List<ProductResponse> response = wishlistService.getFavoriteProducts(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách yêu thích thành công", response));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<WishlistStatusResponse>> addFavorite(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        WishlistStatusResponse response = wishlistService.addFavorite(authentication.getName(), productId);
        return ResponseEntity.ok(ApiResponse.success("Đã thêm sản phẩm vào mục yêu thích", response));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<WishlistStatusResponse>> removeFavorite(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        WishlistStatusResponse response = wishlistService.removeFavorite(authentication.getName(), productId);
        return ResponseEntity.ok(ApiResponse.success("Đã bỏ sản phẩm khỏi mục yêu thích", response));
    }

    @GetMapping("/products/{productId}/exists")
    public ResponseEntity<ApiResponse<WishlistStatusResponse>> isFavorite(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        WishlistStatusResponse response = wishlistService.isFavorite(authentication.getName(), productId);
        return ResponseEntity.ok(ApiResponse.success("Lấy trạng thái yêu thích thành công", response));
    }
}
