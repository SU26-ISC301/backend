package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "wallet_topup_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTopupOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    @Column(name = "payment_transaction_id")
    private String paymentTransactionId;

    /**
     * PENDING_PAYMENT, SUCCESS, FAILED, EXPIRED, CANCELLED
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "PENDING_PAYMENT";

    @Column(name = "paid_at")
    private ZonedDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
