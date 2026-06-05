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
    List<Product> findByVendorIdAndIsActiveTrue(Long vendorId);
    boolean existsBySlug(String slug);
}
