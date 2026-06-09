package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.VendorSubscriptionTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorSubscriptionTransactionRepository extends JpaRepository<VendorSubscriptionTransaction, Long> {

    Optional<VendorSubscriptionTransaction> findByPaymentRef(String paymentRef);

    List<VendorSubscriptionTransaction> findByVendorIdOrderByCreatedAtDesc(Long vendorId);

    Optional<VendorSubscriptionTransaction> findByVendorIdAndStatus(Long vendorId, String status);
}
