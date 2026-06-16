package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "post_promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "initial_budget", nullable = false)
    private BigDecimal initialBudget;

    @Column(name = "remaining_budget", nullable = false)
    private BigDecimal remainingBudget;

    @Column(name = "spent_amount", nullable = false)
    @Builder.Default
    private BigDecimal spentAmount = BigDecimal.ZERO;

    @Column(name = "start_date", nullable = false)
    private ZonedDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private ZonedDateTime endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private VendorWallet wallet;

    @Column(name = "roi_per_click", nullable = false)
    private BigDecimal roiPerClick;

    @Column(name = "estimated_clicks", nullable = false)
    private Integer estimatedClicks;

    @Column(name = "customer_clicks", nullable = false)
    @Builder.Default
    private Integer customerClicks = 0;

    @Column(name = "reserve_transaction_id")
    private Long reserveTransactionId;

    @Column(name = "stopped_at")
    private ZonedDateTime stoppedAt;

    @Column(name = "stop_reason")
    private String stopReason;

    /**
     * DRAFT | ACTIVE | PAUSED | EXHAUSTED | COMPLETED | CANCELLED
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "DRAFT";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
