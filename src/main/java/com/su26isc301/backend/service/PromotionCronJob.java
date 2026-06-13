package com.su26isc301.backend.service;

import com.su26isc301.backend.entity.PostPromotion;
import com.su26isc301.backend.repository.PostPromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionCronJob {

    private final PostPromotionRepository promotionRepository;
    private final WalletService walletService;

    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void processExpiredPromotions() {
        List<PostPromotion> activePromos = promotionRepository.findByStatus("ACTIVE");
        ZonedDateTime now = ZonedDateTime.now();
        
        for (PostPromotion promo : activePromos) {
            if (promo.getEndDate().isBefore(now)) {
                promo.setStatus("COMPLETED");
                if (promo.getRemainingBudget().compareTo(BigDecimal.ZERO) > 0) {
                    walletService.refundBudget(promo.getVendor().getId(), promo.getRemainingBudget(), promo.getId());
                    promo.setRemainingBudget(BigDecimal.ZERO);
                }
                promotionRepository.save(promo);
                log.info("Promotion {} completed and refunded", promo.getId());
            }
        }
    }
}
