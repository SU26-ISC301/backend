package com.su26isc301.backend.service;

import com.su26isc301.backend.entity.VendorReputation;
import com.su26isc301.backend.repository.VendorReputationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class CpcEngine {

    private final VendorReputationRepository reputationRepository;
    
    private static final BigDecimal BASE_CPC = new BigDecimal("1000");

    @Transactional
    public BigDecimal calculateCpc(Long vendorId) {
        VendorReputation rep = reputationRepository.findById(vendorId)
                .orElse(null);
                
        if (rep == null) {
            return BASE_CPC;
        }
        
        // Calculate Reputation Score (0 - 100)
        // Rating: Max 50 points (ratingAverage / 5 * 50)
        BigDecimal ratingScore = rep.getRatingAverage().divide(new BigDecimal("5"), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("50"));
        
        // Success Rate: Max 50 points
        int totalOrders = rep.getCompletedOrders() + rep.getCancelledOrders();
        BigDecimal successRateScore = BigDecimal.ZERO;
        if (totalOrders > 0) {
            successRateScore = new BigDecimal(rep.getCompletedOrders()).divide(new BigDecimal(totalOrders), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("50"));
        } else {
            successRateScore = new BigDecimal("25"); // Neutral for new shops
        }
        
        // Penalty: -10 points per complaint
        BigDecimal penalty = new BigDecimal(rep.getComplaintCount()).multiply(new BigDecimal("10"));
        
        BigDecimal rScore = ratingScore.add(successRateScore).subtract(penalty);
        
        if (rScore.compareTo(BigDecimal.ZERO) < 0) {
            rScore = BigDecimal.ZERO;
        } else if (rScore.compareTo(new BigDecimal("100")) > 0) {
            rScore = new BigDecimal("100");
        }
        
        rep.setReputationScore(rScore);
        rep.setLastCalculatedAt(ZonedDateTime.now());
        reputationRepository.save(rep);
        
        // CPC formula: Final_CPC = BASE_CPC * (2.0 - R_score/100)
        // If R_score = 100 -> CPC = 1000 * 1.0 = 1000
        // If R_score = 0 -> CPC = 1000 * 2.0 = 2000
        BigDecimal coefficient = new BigDecimal("2.0").subtract(rScore.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        BigDecimal finalCpc = BASE_CPC.multiply(coefficient).setScale(0, RoundingMode.HALF_UP);
        
        return finalCpc;
    }
}
