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

    @Query("SELECT SUM(w.amount) FROM WalletTransaction w WHERE w.vendor.id = :vendorId AND w.type = 'TOP_UP' AND w.status = 'SUCCESS'")
    java.math.BigDecimal sumTotalDeposited(@org.springframework.data.repository.query.Param("vendorId") Long vendorId);

    @Query("SELECT SUM(w.amount) FROM WalletTransaction w WHERE w.vendor.id = :vendorId AND w.amount < 0 AND w.status = 'SUCCESS'")
    java.math.BigDecimal sumTotalSpent(@org.springframework.data.repository.query.Param("vendorId") Long vendorId);

}
