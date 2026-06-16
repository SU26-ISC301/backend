package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.WalletTopupOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletTopupOrderRepository extends JpaRepository<WalletTopupOrder, Long> {

    Optional<WalletTopupOrder> findByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM WalletTopupOrder o WHERE o.orderCode = :orderCode")
    Optional<WalletTopupOrder> findByOrderCodeForUpdate(@Param("orderCode") String orderCode);
}
