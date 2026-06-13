package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.SubscriptionQuotaTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionQuotaTransactionRepository extends JpaRepository<SubscriptionQuotaTransaction, Long> {
    List<SubscriptionQuotaTransaction> findByVendorIdOrderByCreatedAtDesc(Long vendorId);
}
