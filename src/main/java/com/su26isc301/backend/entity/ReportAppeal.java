package com.su26isc301.backend.entity;

import com.su26isc301.backend.enums.AppealStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "report_appeals", indexes = {
    @Index(name = "idx_report_appeals_report_id", columnList = "report_id"),
    @Index(name = "idx_report_appeals_vendor_id", columnList = "vendor_id"),
    @Index(name = "idx_report_appeals_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportAppeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "seller_profile_id", nullable = false, columnDefinition = "uuid")
    private UUID sellerProfileId;

    @Column(name = "appeal_reason", nullable = false, columnDefinition = "TEXT")
    private String appealReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AppealStatus status = AppealStatus.SUBMITTED;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewed_by_user_id", columnDefinition = "uuid")
    private UUID reviewedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "reviewed_at")
    private ZonedDateTime reviewedAt;
}
