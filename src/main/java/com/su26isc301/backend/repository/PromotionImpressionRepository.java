package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.PromotionImpression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionImpressionRepository extends JpaRepository<PromotionImpression, Long> {
    long countByPromotionId(Long promotionId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(i) FROM PromotionImpression i WHERE i.promotion.vendor.id = :vendorId")
    Long countMarketingImpressions(@org.springframework.data.repository.query.Param("vendorId") Long vendorId);

    @org.springframework.data.jpa.repository.Query(value = "SELECT TO_CHAR(i.viewed_at, 'YYYY-MM-DD') as date, COUNT(i.id) as impressions " +
           "FROM promotion_impressions i " +
           "JOIN post_promotions p ON i.promotion_id = p.id " +
           "WHERE p.vendor_id = :vendorId " +
           "GROUP BY TO_CHAR(i.viewed_at, 'YYYY-MM-DD') ORDER BY date", nativeQuery = true)
    java.util.List<DailyImpressionStats> getDailyImpressionStats(@org.springframework.data.repository.query.Param("vendorId") Long vendorId);

    interface DailyImpressionStats {
        String getDate();
        Long getImpressions();
    }
}
