package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByVendor(Vendor vendor);
    boolean existsByVendorAndWarehouseNameIgnoreCase(Vendor vendor, String warehouseName);
}
