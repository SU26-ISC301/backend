package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "product_ads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAd {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "bid_amount", nullable = false)
    private BigDecimal bidAmount;

    @Column(name = "daily_budget")
    private BigDecimal dailyBudget;
    
    @Column(name = "total_amount", nullable = false)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "start_date", nullable = false)
    private ZonedDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private ZonedDateTime endDate;

    /**
     * 'PENDING' | 'ACTIVE' | 'PAUSED' | 'EXPIRED'
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "payment_ref", unique = true)
    private String paymentRef;

    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
