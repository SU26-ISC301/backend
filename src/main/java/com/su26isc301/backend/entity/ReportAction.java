package com.su26isc301.backend.entity;

import com.su26isc301.backend.enums.ReportActionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "report_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ReportActionType actionType;

    @Column(name = "action_note", columnDefinition = "TEXT")
    private String actionNote;

    @Column(name = "action_by_user_id", nullable = false, columnDefinition = "uuid")
    private UUID actionByUserId;

    @Column(name = "action_by_email")
    private String actionByEmail;

    @Column(name = "expires_at")
    private ZonedDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
