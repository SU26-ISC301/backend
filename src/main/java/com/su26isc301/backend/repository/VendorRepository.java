package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {
    Optional<Vendor> findByProfile(Profile profile);
    Optional<Vendor> findByProfileId(UUID profileId);
}
