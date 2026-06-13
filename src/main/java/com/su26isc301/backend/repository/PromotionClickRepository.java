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
}
