package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.VendorSubscriptionTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorSubscriptionTransactionRepository extends JpaRepository<VendorSubscriptionTransaction, Long> {

    Optional<VendorSubscriptionTransaction> findByPaymentRef(String paymentRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from VendorSubscriptionTransaction t where t.paymentRef = :paymentRef")
    Optional<VendorSubscriptionTransaction> findByPaymentRefWithLock(@Param("paymentRef") String paymentRef);

    List<VendorSubscriptionTransaction> findByVendorIdOrderByCreatedAtDesc(Long vendorId);

    Optional<VendorSubscriptionTransaction> findByVendorIdAndStatus(Long vendorId, String status);
}
