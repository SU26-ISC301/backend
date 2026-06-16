package com.su26isc301.backend.service;

import com.su26isc301.backend.entity.*;
import com.su26isc301.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PostPromotionRepository promotionRepository;
    private final PromotionImpressionRepository impressionRepository;
    private final PromotionClickRepository clickRepository;
    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private final ProfileRepository profileRepository;
    
    private final WalletService walletService;
    private final ClickValidationService clickValidationService;
    private final VendorWalletRepository walletRepository;

    @Transactional
    public PostPromotion createPromotion(Long vendorId, Long productId, BigDecimal promotionAmount, BigDecimal roiPerClick, ZonedDateTime startDate, ZonedDateTime endDate) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
                
        if (!product.getVendor().getId().equals(vendorId)) {
            throw new RuntimeException("Bạn không có quyền quảng cáo sản phẩm này");
        }
        
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw new RuntimeException("Chỉ sản phẩm ACTIVE mới được quảng bá");
        }

        if (promotionAmount.compareTo(new BigDecimal("100000")) < 0) {
            throw new RuntimeException("Ngân sách tối thiểu là 100.000 VNĐ");
        }

        if (roiPerClick.compareTo(new BigDecimal("1000")) < 0) {
            throw new RuntimeException("ROI/click tối thiểu là 1.000 VNĐ");
        }

        Integer estimatedClicks = promotionAmount.divide(roiPerClick, 0, RoundingMode.FLOOR).intValue();

        VendorWallet wallet = walletRepository.findByVendorIdForUpdate(vendorId)
                .orElseGet(() -> walletService.getOrCreateWallet(vendorId));

        if (wallet.getAvailableBalance().compareTo(promotionAmount) < 0) {
            throw new RuntimeException("INSUFFICIENT_ACCOUNT_BALANCE");
        }

        ZonedDateTime now = ZonedDateTime.now();
        String status = startDate.isAfter(now) ? "SCHEDULED" : "ACTIVE";

        PostPromotion promotion = PostPromotion.builder()
                .vendor(vendor)
                .product(product)
                .wallet(wallet)
                .initialBudget(promotionAmount)
                .remainingBudget(promotionAmount)
                .spentAmount(BigDecimal.ZERO)
                .roiPerClick(roiPerClick)
                .estimatedClicks(estimatedClicks)
                .customerClicks(0)
                .startDate(startDate)
                .endDate(endDate)
                .status(status)
                .build();
                
        promotion = promotionRepository.save(promotion);
        
        // Reserve budget
        walletService.deductGeneric(vendorId, promotionAmount, "PROMOTION_RESERVE", promotion.getId());
        
        return promotion;
    }

    @Transactional
    public void recordImpression(Long promotionId, String sessionId, java.util.UUID viewerId, String surface) {
        PostPromotion promotion = promotionRepository.findById(promotionId).orElse(null);
        if (promotion == null || !"ACTIVE".equals(promotion.getStatus())) return;
        
        Profile viewer = null;
        if (viewerId != null) {
            viewer = profileRepository.findById(viewerId).orElse(null);
        }
        
        PromotionImpression impression = PromotionImpression.builder()
                .promotion(promotion)
                .product(promotion.getProduct())
                .viewer(viewer)
                .sessionId(sessionId)
                .surface(surface)
                .build();
        impressionRepository.save(impression);
    }

    @Transactional
    public void recordClick(Long promotionId, String sessionId, java.util.UUID viewerId, String surface) {
        PostPromotion promotion = promotionRepository.findById(promotionId).orElse(null);
        if (promotion == null) return;
        
        if (!"ACTIVE".equals(promotion.getStatus())) {
            logClickAsInvalid(promotion, viewerId, sessionId, surface, "PROMOTION_NOT_ACTIVE");
            return;
        }

        ZonedDateTime now = ZonedDateTime.now();
        if (now.isBefore(promotion.getStartDate()) || now.isAfter(promotion.getEndDate())) {
            logClickAsInvalid(promotion, viewerId, sessionId, surface, "OUT_OF_TIME");
            return;
        }

        Profile viewer = null;
        if (viewerId != null) {
            viewer = profileRepository.findById(viewerId).orElse(null);
        }

        String validationResult = clickValidationService.validateClick(promotion, sessionId, viewer);
        boolean isCustomerClick = "VALID".equals(validationResult);

        if (!isCustomerClick) {
            logClickAsInvalid(promotion, viewerId, sessionId, surface, validationResult);
            return;
        }

        BigDecimal chargeAmount = promotion.getRoiPerClick();

        if (promotion.getRemainingBudget().compareTo(chargeAmount) < 0) {
            promotion.setStatus("EXHAUSTED");
            promotionRepository.save(promotion);
            logClickAsInvalid(promotion, viewerId, sessionId, surface, "NO_BUDGET");
            return;
        }

        // Valid customer click
        promotion.setRemainingBudget(promotion.getRemainingBudget().subtract(chargeAmount));
        promotion.setSpentAmount(promotion.getSpentAmount().add(chargeAmount));
        promotion.setCustomerClicks(promotion.getCustomerClicks() + 1);

        if (promotion.getRemainingBudget().compareTo(promotion.getRoiPerClick()) < 0) {
            promotion.setStatus("EXHAUSTED");
        }
        
        promotionRepository.save(promotion);

        walletService.deductGeneric(promotion.getVendor().getId(), chargeAmount, "PROMOTION_CLICK_CHARGE", promotion.getId());

        PromotionClick click = PromotionClick.builder()
                .promotion(promotion)
                .product(promotion.getProduct())
                .viewer(viewer)
                .sessionId(sessionId)
                .roiAmountSnapshot(chargeAmount)
                .isCustomerClick(true)
                .isCharged(true)
                .surface(surface)
                .build();
        clickRepository.save(click);
    }

    private void logClickAsInvalid(PostPromotion promotion, java.util.UUID viewerId, String sessionId, String surface, String reason) {
        Profile viewer = null;
        if (viewerId != null) {
            viewer = profileRepository.findById(viewerId).orElse(null);
        }
        PromotionClick click = PromotionClick.builder()
                .promotion(promotion)
                .product(promotion.getProduct())
                .viewer(viewer)
                .sessionId(sessionId)
                .roiAmountSnapshot(promotion.getRoiPerClick() != null ? promotion.getRoiPerClick() : BigDecimal.ZERO)
                .isCustomerClick(false)
                .isCharged(false)
                .invalidReason(reason)
                .surface(surface)
                .build();
        clickRepository.save(click);
    }

    @Transactional
    public PostPromotion stopPromotion(Long promotionId, Long vendorId, boolean confirm, String reason) {
        if (!confirm) {
            throw new RuntimeException("PROMOTION_STOP_CONFIRM_REQUIRED");
        }

        PostPromotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy promotion"));

        if (!promotion.getVendor().getId().equals(vendorId)) {
            throw new RuntimeException("Không có quyền");
        }

        if (!"ACTIVE".equals(promotion.getStatus()) && !"SCHEDULED".equals(promotion.getStatus()) && !"PAUSED".equals(promotion.getStatus())) {
            return promotion;
        }

        BigDecimal remaining = promotion.getRemainingBudget();
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            walletService.deductGeneric(vendorId, remaining.negate(), "PROMOTION_RELEASE", promotion.getId());
            promotion.setRemainingBudget(BigDecimal.ZERO);
        }

        promotion.setStatus("STOPPED");
        promotion.setStoppedAt(ZonedDateTime.now());
        promotion.setStopReason(reason);
        return promotionRepository.save(promotion);
    }
}
