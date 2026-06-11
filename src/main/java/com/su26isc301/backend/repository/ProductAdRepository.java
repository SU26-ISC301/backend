package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.ProductAd;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductAdRepository extends JpaRepository<ProductAd, Long> {

    Optional<ProductAd> findByPaymentRef(String paymentRef);

    Page<ProductAd> findByVendorId(Long vendorId, Pageable pageable);

    @Query("SELECT pa FROM ProductAd pa WHERE pa.status = 'ACTIVE' AND pa.startDate <= :now AND pa.endDate >= :now ORDER BY pa.bidAmount DESC")
    Page<ProductAd> findActiveAdsWithBidding(@Param("now") ZonedDateTime now, Pageable pageable);
    
    @Query("SELECT pa FROM ProductAd pa WHERE pa.status = 'ACTIVE' AND pa.startDate <= :now AND pa.endDate >= :now ORDER BY pa.bidAmount DESC")
    List<ProductAd> findAllActiveAdsWithBidding(@Param("now") ZonedDateTime now);
}
