package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.VendorWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VendorWalletRepository extends JpaRepository<VendorWallet, Long> {

    Optional<VendorWallet> findByVendorId(Long vendorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM VendorWallet w WHERE w.vendor.id = :vendorId")
    Optional<VendorWallet> findByVendorIdForUpdate(@Param("vendorId") Long vendorId);
}
