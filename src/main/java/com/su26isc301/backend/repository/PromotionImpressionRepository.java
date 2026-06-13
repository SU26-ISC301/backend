package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.PromotionImpression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionImpressionRepository extends JpaRepository<PromotionImpression, Long> {
    long countByPromotionId(Long promotionId);
}
