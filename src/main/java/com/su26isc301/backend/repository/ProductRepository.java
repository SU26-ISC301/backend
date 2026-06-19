package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByVendorId(Long vendorId);
    List<Product> findByCategoryId(Long categoryId);
    
    Optional<Product> findByIdAndIsActiveTrue(Long id);
    List<Product> findByIsActiveTrueOrderByCreatedAtDesc();
    List<Product> findByStatusIgnoreCaseAndIsActiveTrueOrderByCreatedAtDesc(String status);
    List<Product> findByVendorIdAndIsActiveTrue(Long vendorId);
    boolean existsBySlug(String slug);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM Product p WHERE p.isActive = true AND UPPER(p.status) = 'ACTIVE' " +
           "AND (COALESCE(:categoryId, 0) = 0 OR p.category.id = :categoryId) " +
           "AND (COALESCE(:vendorId, 0) = 0 OR p.vendor.id = :vendorId) " +
           "AND (COALESCE(:keyword, '') = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Product> searchActiveProducts(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("categoryId") Long categoryId,
            @org.springframework.data.repository.query.Param("vendorId") Long vendorId);
}
