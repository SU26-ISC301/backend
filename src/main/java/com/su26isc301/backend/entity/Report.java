package com.su26isc301.backend.entity;

import com.su26isc301.backend.enums.ReportPriority;
import com.su26isc301.backend.enums.ReportReasonCode;
import com.su26isc301.backend.enums.ReportStatus;
import com.su26isc301.backend.enums.ReportTargetType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "reports", indexes = {
    @Index(name = "idx_reports_reporter_user_id", columnList = "reporter_user_id"),
    @Index(name = "idx_reports_vendor_id", columnList = "vendor_id"),
    @Index(name = "idx_reports_product_id", columnList = "product_id"),
    @Index(name = "idx_reports_conversation_id", columnList = "conversation_id"),
    @Index(name = "idx_reports_status", columnList = "status"),
    @Index(name = "idx_reports_priority", columnList = "priority"),
    @Index(name = "idx_reports_reason_code", columnList = "reason_code"),
    @Index(name = "idx_reports_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_user_id", nullable = false, columnDefinition = "uuid")
    private UUID reporterUserId;

    @Column(name = "reporter_email_snapshot")
    private String reporterEmailSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "seller_profile_id", columnDefinition = "uuid")
    private UUID sellerProfileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false)
    private ReportReasonCode reasonCode;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReportStatus status = ReportStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private ReportPriority priority;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
