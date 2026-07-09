package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.SellerViolation;
import com.su26isc301.backend.enums.ViolationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface SellerViolationRepository extends JpaRepository<SellerViolation, Long> {
    List<SellerViolation> findByVendorId(Long vendorId);
    List<SellerViolation> findByVendorIdAndStatus(Long vendorId, ViolationStatus status);
    
    // Tìm các vi phạm hoạt động chưa hết hạn hoặc không có hạn hết hạn
    List<SellerViolation> findByVendorIdAndStatusAndViolationType(Long vendorId, ViolationStatus status, String violationType);
}
