package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.ShopVisit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShopVisitRepository extends JpaRepository<ShopVisit, Long> {

    @Query("SELECT COUNT(v) > 0 FROM ShopVisit v WHERE v.vendor.id = :vendorId " +
           "AND ((:productId IS NULL AND v.product IS NULL) OR (v.product.id = :productId)) " +
           "AND (v.ipAddress = :ipAddress OR (:userId IS NOT NULL AND v.user.id = :userId)) " +
           "AND v.createdAt > :time")
    boolean checkRecentVisit(
        @Param("vendorId") Long vendorId,
        @Param("productId") Long productId,
        @Param("ipAddress") String ipAddress,
        @Param("userId") UUID userId,
        @Param("time") ZonedDateTime time
    );

    @Query("SELECT COUNT(v) FROM ShopVisit v WHERE v.vendor.id = :vendorId AND v.createdAt >= :startOfDay")
    long countTodayVisits(
        @Param("vendorId") Long vendorId,
        @Param("startOfDay") ZonedDateTime startOfDay
    );

    @Query("SELECT COUNT(v) FROM ShopVisit v WHERE v.vendor.id = :vendorId")
    long countTotalVisits(@Param("vendorId") Long vendorId);

    @Query("SELECT CAST(v.createdAt AS date) as day, COUNT(v) FROM ShopVisit v " +
           "WHERE v.vendor.id = :vendorId AND v.createdAt >= :startDate " +
           "GROUP BY CAST(v.createdAt AS date) " +
           "ORDER BY day ASC")
    List<Object[]> findDailyVisits(
        @Param("vendorId") Long vendorId,
        @Param("startDate") ZonedDateTime startDate
    );

    @Query("SELECT v.product, COUNT(v) as visitCount FROM ShopVisit v " +
           "WHERE v.vendor.id = :vendorId AND v.product IS NOT NULL " +
           "GROUP BY v.product " +
           "ORDER BY visitCount DESC")
    List<Object[]> findTopVisitedProducts(
        @Param("vendorId") Long vendorId,
        Pageable pageable
    );
}
