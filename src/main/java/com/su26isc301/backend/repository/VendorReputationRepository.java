package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.VendorReputation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VendorReputationRepository extends JpaRepository<VendorReputation, Long> {
}
