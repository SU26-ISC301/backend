package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(nullable = false)
    private String type; // 'PICKUP' or 'RETURN'

    @Column(name = "warehouse_name", nullable = false)
    private String warehouseName;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String province;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private String ward;

    @Column(name = "address_detail", nullable = false)
    private String addressDetail;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "shipping_regions", columnDefinition = "jsonb")
    private String shippingRegions;
}
