package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.PromotionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromotionConfigRepository extends JpaRepository<PromotionConfig, Long> {

    Optional<PromotionConfig> findByConfigKeyAndIsActiveTrue(String configKey);
}
