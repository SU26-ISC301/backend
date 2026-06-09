package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "vendor_subscription_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorSubscriptionTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "plan_type", nullable = false)
    private String planType;

    /**
     * Số tiền VNĐ (nguyên)
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /**
     * 'payos' | 'vnpay' | 'bank_transfer'
     */
    @Column(name = "payment_method")
    private String paymentMethod;

    /**
     * Mã order từ cổng thanh toán (unique)
     */
    @Column(name = "payment_ref", unique = true)
    private String paymentRef;

    /**
     * 'pending' | 'paid' | 'failed' | 'cancelled'
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "pending";

    /**
     * URL thanh toán từ PayOS
     */
    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    @Column(name = "paid_at")
    private ZonedDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
