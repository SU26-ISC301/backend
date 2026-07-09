package com.su26isc301.backend.entity;

import com.su26isc301.backend.enums.ReportPriority;
import com.su26isc301.backend.enums.ViolationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "seller_violations", indexes = {
    @Index(name = "idx_seller_violations_vendor_id", columnList = "vendor_id"),
    @Index(name = "idx_seller_violations_seller_profile_id", columnList = "seller_profile_id"),
    @Index(name = "idx_seller_violations_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "seller_profile_id", nullable = false, columnDefinition = "uuid")
    private UUID sellerProfileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "violation_type", nullable = false)
    private String violationType; // e.g. RESTRICT_LISTING, MISLEADING_LISTING, etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private ReportPriority severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ViolationStatus status = ViolationStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "expires_at")
    private ZonedDateTime expiresAt;
}
