package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.PostPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostPromotionRepository extends JpaRepository<PostPromotion, Long> {
    List<PostPromotion> findByVendorId(Long vendorId);
    List<PostPromotion> findByStatus(String status);
    List<PostPromotion> findByProductIdInAndStatus(List<Long> productIds, String status);
}
