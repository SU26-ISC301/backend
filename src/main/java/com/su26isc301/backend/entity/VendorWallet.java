package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "vendor_wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false, unique = true)
    private Vendor vendor;

    @Column(name = "available_balance", nullable = false)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "locked_balance", nullable = false)
    @Builder.Default
    private BigDecimal lockedBalance = BigDecimal.ZERO;

    @Column(name = "total_deposited", nullable = false)
    @Builder.Default
    private BigDecimal totalDeposited = BigDecimal.ZERO;

    @Column(name = "total_spent", nullable = false)
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false)
    @Builder.Default
    private String currency = "VND";

    /**
     * ACTIVE, LOCKED, CLOSED
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
