package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    boolean existsByUserIdAndProductId(UUID userId, Long productId);

    Optional<Wishlist> findByUserIdAndProductId(UUID userId, Long productId);

    List<Wishlist> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
