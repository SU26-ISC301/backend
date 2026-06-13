package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(nullable = false)
    private BigDecimal amount;

    /**
     * TOP_UP, DEDUCTION, REFUND
     */
    @Column(name = "transaction_type", nullable = false)
    private String transactionType;

    /**
     * Mã order từ cổng thanh toán (dành cho TOP_UP)
     */
    @Column(name = "payment_ref", unique = true)
    private String paymentRef;

    /**
     * Mã chiến dịch quảng bá liên quan (dành cho DEDUCTION, REFUND)
     */
    @Column(name = "promotion_id")
    private Long promotionId;

    /**
     * PENDING, SUCCESS, FAILED
     */
    @Column(nullable = false)
    private String status;

    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
