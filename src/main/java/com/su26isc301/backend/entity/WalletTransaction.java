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
    @JoinColumn(name = "wallet_id", nullable = false)
    private VendorWallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "transaction_code", nullable = false, unique = true)
    private String transactionCode;

    @Column(nullable = false)
    private BigDecimal amount;

    /**
     * TOP_UP, PROMOTION_RESERVE, PROMOTION_CLICK_CHARGE, PROMOTION_RELEASE, ADMIN_ADJUST
     */
    @Column(name = "transaction_type", nullable = false)
    private String type;

    @Column(name = "available_before", nullable = false)
    private BigDecimal availableBefore;

    @Column(name = "available_after", nullable = false)
    private BigDecimal availableAfter;

    @Column(name = "locked_before", nullable = false)
    private BigDecimal lockedBefore;

    @Column(name = "locked_after", nullable = false)
    private BigDecimal lockedAfter;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "payment_transaction_id")
    private String paymentTransactionId;

    @Column(name = "status", nullable = false)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
