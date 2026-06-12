package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.response.WishlistStatusResponse;
import com.su26isc301.backend.dto.response.ProductResponse;
import com.su26isc301.backend.entity.Product;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Wishlist;
import com.su26isc301.backend.enums.ProductStatus;
import com.su26isc301.backend.repository.ProductRepository;
import com.su26isc301.backend.repository.ProfileRepository;
import com.su26isc301.backend.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProfileRepository profileRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    @Transactional
    public WishlistStatusResponse addFavorite(String email, Long productId) {
        Profile user = resolveUser(email);
        Product product = productRepository.findByIdAndIsActiveTrue(productId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm đang bán"));

        if (!wishlistRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            wishlistRepository.save(Wishlist.builder()
                    .user(user)
                    .product(product)
                    .build());
        }

        return WishlistStatusResponse.builder()
                .productId(productId)
                .favorite(true)
                .build();
    }

    @Transactional
    public WishlistStatusResponse removeFavorite(String email, Long productId) {
        Profile user = resolveUser(email);
        wishlistRepository.findByUserIdAndProductId(user.getId(), productId)
                .ifPresent(wishlistRepository::delete);

        return WishlistStatusResponse.builder()
                .productId(productId)
                .favorite(false)
                .build();
    }

    @Transactional(readOnly = true)
    public WishlistStatusResponse isFavorite(String email, Long productId) {
        Profile user = resolveUser(email);
        return WishlistStatusResponse.builder()
                .productId(productId)
                .favorite(wishlistRepository.existsByUserIdAndProductId(user.getId(), productId))
                .build();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getFavoriteProducts(String email) {
        Profile user = resolveUser(email);
        Set<Long> seenProductIds = new HashSet<>();
        return wishlistRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(Wishlist::getProduct)
                .filter(product -> Boolean.TRUE.equals(product.getIsActive()))
                .filter(product -> ProductStatus.ACTIVE.getValue().equalsIgnoreCase(product.getStatus()))
                .filter(product -> seenProductIds.add(product.getId()))
                .map(productService::mapPublicProduct)
                .collect(Collectors.toList());
    }

    private Profile resolveUser(String email) {
        return profileRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản người mua"));
    }
}
