package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.Banner;
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
public interface BannerRepository extends JpaRepository<Banner, Long> {

    Optional<Banner> findByPaymentRef(String paymentRef);

    Page<Banner> findByVendorId(Long vendorId, Pageable pageable);

    @Query("SELECT b FROM Banner b WHERE b.status = 'ACTIVE' AND b.position = :position AND b.startDate <= :now AND b.endDate >= :now ORDER BY b.pricePaid DESC")
    List<Banner> findActiveBannersByPosition(@Param("position") String position, @Param("now") ZonedDateTime now);
}
