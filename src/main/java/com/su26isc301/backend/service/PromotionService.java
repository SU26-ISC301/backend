package com.su26isc301.backend.service;

import com.su26isc301.backend.entity.*;
import com.su26isc301.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final VendorReputationRepository reputationRepository;
    
    private final WalletService walletService;
    private final CpcEngine cpcEngine;
    private final ClickValidationService clickValidationService;

    @Transactional
    public PostPromotion createPromotion(Long vendorId, Long productId, int days, BigDecimal budget) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
                
        if (!product.getVendor().getId().equals(vendorId)) {
            throw new RuntimeException("Bạn không có quyền quảng cáo sản phẩm này");
        }
        
        if (budget.compareTo(new BigDecimal("10000")) < 0) {
            throw new RuntimeException("Ngân sách tối thiểu là 10.000 VNĐ");
        }

        ZonedDateTime now = ZonedDateTime.now();
        PostPromotion promotion = PostPromotion.builder()
                .vendor(vendor)
                .product(product)
                .initialBudget(budget)
                .remainingBudget(budget)
                .spentAmount(BigDecimal.ZERO)
                .startDate(now)
                .endDate(now.plusDays(days))
                .status("ACTIVE")
                .build();
                
        promotion = promotionRepository.save(promotion);
        
        walletService.deductBudget(vendorId, budget, promotion.getId());
        
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
                .viewer(viewer)
                .sessionId(sessionId)
                .surface(surface)
                .build();
        impressionRepository.save(impression);
    }

    @Transactional
    public void recordClick(Long promotionId, String sessionId, java.util.UUID viewerId) {
        PostPromotion promotion = promotionRepository.findById(promotionId).orElse(null);
        if (promotion == null || !"ACTIVE".equals(promotion.getStatus())) return;

        Profile viewer = null;
        if (viewerId != null) {
            viewer = profileRepository.findById(viewerId).orElse(null);
        }

        String validationResult = clickValidationService.validateClick(promotion, sessionId, viewer);
        
        BigDecimal cpc = cpcEngine.calculateCpc(promotion.getVendor().getId());
        
        VendorReputation rep = reputationRepository.findById(promotion.getVendor().getId()).orElse(null);
        BigDecimal repScore = rep != null ? rep.getReputationScore() : new BigDecimal("30");
                
        boolean isCharged = "VALID".equals(validationResult);
        
        PromotionClick click = PromotionClick.builder()
                .promotion(promotion)
                .viewer(viewer)
                .sessionId(sessionId)
                .cpcAmount(cpc)
                .reputationScoreSnapshot(repScore)
                .isCharged(isCharged)
                .invalidReason(isCharged ? null : validationResult)
                .build();
                
        clickRepository.save(click);

        if (isCharged) {
            BigDecimal newRemaining = promotion.getRemainingBudget().subtract(cpc);
            if (newRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                promotion.setSpentAmount(promotion.getSpentAmount().add(promotion.getRemainingBudget()));
                promotion.setRemainingBudget(BigDecimal.ZERO);
                promotion.setStatus("EXHAUSTED");
            } else {
                promotion.setRemainingBudget(newRemaining);
                promotion.setSpentAmount(promotion.getSpentAmount().add(cpc));
            }
            promotionRepository.save(promotion);
        }
    }
}
