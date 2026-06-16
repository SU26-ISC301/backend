package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_products_vendor_id", columnList = "vendor_id"),
    @Index(name = "idx_products_category_id", columnList = "category_id"),
    @Index(name = "idx_products_status", columnList = "status"),
    @Index(name = "idx_products_is_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sold_count")
    @Builder.Default
    private Integer soldCount = 0;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "status")
    @Builder.Default
    private String status = "draft";

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "contains_dangerous_goods")
    @Builder.Default
    private String containsDangerousGoods = "no";

    @Column(name = "dangerous_goods_type")
    private String dangerousGoodsType;

    @Column(name = "safety_warning", columnDefinition = "TEXT")
    private String safetyWarning;

    @Column(name = "warranty_type")
    private String warrantyType;

    @Column(name = "origin_country")
    private String originCountry;

    @Column(name = "condition")
    @Builder.Default
    private String condition = "new";

    @Column(name = "parcel_weight_g")
    private Integer parcelWeightG;

    @Column(name = "parcel_width")
    private Integer parcelWidth;

    @Column(name = "parcel_length")
    private Integer parcelLength;

    @Column(name = "parcel_height")
    private Integer parcelHeight;

    @Column(name = "delivery_method")
    @Builder.Default
    private String deliveryMethod = "default";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductMedia> mediaList;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductAttribute> attributes;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants;
}