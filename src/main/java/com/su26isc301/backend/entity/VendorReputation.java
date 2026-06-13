package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "vendor_reputation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorReputation {

    @Id
    @Column(name = "vendor_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Column(name = "rating_average", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal ratingAverage = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private Integer ratingCount = 0;

    @Column(name = "completed_orders", nullable = false)
    @Builder.Default
    private Integer completedOrders = 0;

    @Column(name = "cancelled_orders", nullable = false)
    @Builder.Default
    private Integer cancelledOrders = 0;

    @Column(name = "complaint_count", nullable = false)
    @Builder.Default
    private Integer complaintCount = 0;

    @Column(name = "reputation_score", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal reputationScore = new BigDecimal("30.00"); // default for new shop

    @Column(name = "last_calculated_at")
    private ZonedDateTime lastCalculatedAt;
}
