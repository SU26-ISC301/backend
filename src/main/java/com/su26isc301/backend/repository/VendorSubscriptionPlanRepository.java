package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.VendorSubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VendorSubscriptionPlanRepository extends JpaRepository<VendorSubscriptionPlan, Long> {

    Optional<VendorSubscriptionPlan> findByVendorId(Long vendorId);

    Optional<VendorSubscriptionPlan> findByVendorIdAndIsActiveTrue(Long vendorId);

    boolean existsByVendorId(Long vendorId);
}
