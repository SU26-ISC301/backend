package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.Conversation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @EntityGraph(attributePaths = {"customer"})
    List<Conversation> findByVendorIdOrderByUpdatedAtDesc(Long vendorId);

    @EntityGraph(attributePaths = {"vendor.profile", "customer"})
    Optional<Conversation> findByIdAndVendorId(Long id, Long vendorId);

    @EntityGraph(attributePaths = {"vendor", "customer"})
    List<Conversation> findByCustomerIdOrderByUpdatedAtDesc(UUID customerId);

    @EntityGraph(attributePaths = {"vendor.profile", "customer"})
    Optional<Conversation> findByIdAndCustomerId(Long id, UUID customerId);

    Optional<Conversation> findByVendorIdAndCustomerId(Long vendorId, UUID customerId);
}
