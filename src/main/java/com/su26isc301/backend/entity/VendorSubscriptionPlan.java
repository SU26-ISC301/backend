package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "vendor_subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorSubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    /**
     * 'free' | 'plus' | 'premium'
     */
    @Column(name = "plan_type", nullable = false)
    @Builder.Default
    private String planType = "free";

    /**
     * -1 = không giới hạn (premium)
     */
    @Column(name = "total_slots", nullable = false)
    @Builder.Default
    private Integer totalSlots = 3;

    @Column(name = "used_slots", nullable = false)
    @Builder.Default
    private Integer usedSlots = 0;

    @CreationTimestamp
    @Column(name = "started_at")
    private ZonedDateTime startedAt;

    /**
     * NULL = không hết hạn
     */
    @Column(name = "expires_at")
    private ZonedDateTime expiresAt;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
