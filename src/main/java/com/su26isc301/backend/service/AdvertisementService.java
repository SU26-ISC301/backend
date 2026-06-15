package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.request.BannerCreateRequest;
import com.su26isc301.backend.dto.request.ProductAdCreateRequest;
import com.su26isc301.backend.dto.response.BannerResponse;
import com.su26isc301.backend.dto.response.ProductAdResponse;
import com.su26isc301.backend.entity.Banner;
import com.su26isc301.backend.entity.Product;
import com.su26isc301.backend.entity.ProductAd;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.repository.BannerRepository;
import com.su26isc301.backend.repository.ProductAdRepository;
import com.su26isc301.backend.repository.ProductRepository;
import com.su26isc301.backend.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvertisementService {

    private final ProductAdRepository productAdRepository;
    private final BannerRepository bannerRepository;
    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private final WalletService walletService;

    @Transactional
    public ProductAdResponse createProductAd(String email, ProductAdCreateRequest request) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getVendor().getId().equals(vendor.getId())) {
            throw new RuntimeException("You do not own this product");
        }

        BigDecimal dailyBudget = request.getTotalAmount().divide(new BigDecimal(request.getDays()), 0, RoundingMode.HALF_UP);
        ZonedDateTime now = ZonedDateTime.now();
        
        ProductAd productAd = ProductAd.builder()
                .product(product)
                .vendor(vendor)
                .totalAmount(request.getTotalAmount())
                .bidAmount(dailyBudget)
                .dailyBudget(dailyBudget)
                .startDate(now)
                .endDate(now.plusDays(request.getDays()))
                .status("ACTIVE")
                .build();

        productAd = productAdRepository.save(productAd);

        // Deduct from wallet
        walletService.deductForProductAd(vendor.getId(), request.getTotalAmount(), productAd.getId());

        return mapToProductAdResponse(productAd);
    }

    @Transactional
    public BannerResponse createBanner(String email, BannerCreateRequest request) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        ZonedDateTime now = ZonedDateTime.now();

        Banner banner = Banner.builder()
                .vendor(vendor)
                .imageUrl(request.getImageUrl())
                .targetUrl(request.getTargetUrl())
                .position(request.getPosition())
                .pricePaid(request.getTotalAmount())
                .startDate(now)
                .endDate(now.plusDays(request.getDays()))
                .status("ACTIVE")
                .build();

        banner = bannerRepository.save(banner);

        // Deduct from wallet
        walletService.deductForBanner(vendor.getId(), request.getTotalAmount(), banner.getId());

        return mapToBannerResponse(banner);
    }

    public Page<ProductAdResponse> getVendorProductAds(String email, Pageable pageable) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));
        return productAdRepository.findByVendorId(vendor.getId(), pageable).map(this::mapToProductAdResponse);
    }

    public Page<BannerResponse> getVendorBanners(String email, Pageable pageable) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));
        return bannerRepository.findByVendorId(vendor.getId(), pageable).map(this::mapToBannerResponse);
    }

    public Page<ProductAdResponse> getActiveProductAds(Pageable pageable) {
        return productAdRepository.findActiveAdsWithBidding(ZonedDateTime.now(), pageable)
                .map(this::mapToProductAdResponse);
    }

    public List<BannerResponse> getActiveBanners(String position) {
        return bannerRepository.findActiveBannersByPosition(position, ZonedDateTime.now())
                .stream().map(this::mapToBannerResponse).collect(Collectors.toList());
    }

    private ProductAdResponse mapToProductAdResponse(ProductAd ad) {
        return ProductAdResponse.builder()
                .id(ad.getId())
                .productId(ad.getProduct().getId())
                .productName(ad.getProduct().getName())
                .vendorId(ad.getVendor().getId())
                .shopName(ad.getVendor().getShopName())
                .bidAmount(ad.getBidAmount())
                .totalAmount(ad.getTotalAmount())
                .startDate(ad.getStartDate())
                .endDate(ad.getEndDate())
                .status(ad.getStatus())
                .paymentUrl(ad.getPaymentUrl())
                .createdAt(ad.getCreatedAt())
                .build();
    }

    private BannerResponse mapToBannerResponse(Banner banner) {
        return BannerResponse.builder()
                .id(banner.getId())
                .vendorId(banner.getVendor() != null ? banner.getVendor().getId() : null)
                .shopName(banner.getVendor() != null ? banner.getVendor().getShopName() : null)
                .imageUrl(banner.getImageUrl())
                .targetUrl(banner.getTargetUrl())
                .position(banner.getPosition())
                .pricePaid(banner.getPricePaid())
                .startDate(banner.getStartDate())
                .endDate(banner.getEndDate())
                .status(banner.getStatus())
                .paymentUrl(banner.getPaymentUrl())
                .createdAt(banner.getCreatedAt())
                .build();
    }
}
