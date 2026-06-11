package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "banners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "target_url")
    private String targetUrl;

    @Column(name = "position", nullable = false)
    private String position;

    @Column(name = "price_paid", nullable = false)
    @Builder.Default
    private BigDecimal pricePaid = BigDecimal.ZERO;

    @Column(name = "start_date", nullable = false)
    private ZonedDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private ZonedDateTime endDate;

    /**
     * 'PENDING' | 'ACTIVE' | 'EXPIRED'
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
