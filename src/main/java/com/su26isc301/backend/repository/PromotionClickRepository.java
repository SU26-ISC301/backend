package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.PromotionClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface PromotionClickRepository extends JpaRepository<PromotionClick, Long> {
    long countByPromotionIdAndIsChargedTrue(Long promotionId);
    boolean existsByPromotionIdAndSessionIdAndClickedAtAfter(Long promotionId, String sessionId, ZonedDateTime after);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(c.roiAmountSnapshot) FROM PromotionClick c WHERE c.promotion.vendor.id = :vendorId AND c.isCharged = true")
    java.math.BigDecimal sumMarketingSpent(@org.springframework.data.repository.query.Param("vendorId") Long vendorId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) FROM PromotionClick c WHERE c.promotion.vendor.id = :vendorId")
    Long countMarketingClicks(@org.springframework.data.repository.query.Param("vendorId") Long vendorId);

    @org.springframework.data.jpa.repository.Query(value = "SELECT TO_CHAR(c.clicked_at, 'YYYY-MM-DD') as date, SUM(c.roi_amount_snapshot) as spend, COUNT(c.id) as clicks " +
           "FROM promotion_clicks c " +
           "JOIN post_promotions p ON c.promotion_id = p.id " +
           "WHERE p.vendor_id = :vendorId AND c.is_charged = true " +
           "GROUP BY TO_CHAR(c.clicked_at, 'YYYY-MM-DD') ORDER BY date", nativeQuery = true)
    List<DailyClickStats> getDailyClickStats(@org.springframework.data.repository.query.Param("vendorId") Long vendorId);

    interface DailyClickStats {
        String getDate();
        java.math.BigDecimal getSpend();
        Long getClicks();
    }
}
