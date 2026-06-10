package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    List<UserDevice> findByProfile(Profile profile);
    Optional<UserDevice> findByProfileAndDeviceToken(Profile profile, String deviceToken);
}
