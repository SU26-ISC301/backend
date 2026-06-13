package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.WalletTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByVendorIdOrderByCreatedAtDesc(Long vendorId);

    Optional<WalletTransaction> findByPaymentRef(String paymentRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletTransaction w WHERE w.paymentRef = :paymentRef")
    Optional<WalletTransaction> findByPaymentRefWithLock(String paymentRef);
}
