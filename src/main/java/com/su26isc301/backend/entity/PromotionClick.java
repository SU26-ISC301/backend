package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "promotion_clicks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    private PostPromotion promotion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viewer_id")
    private Profile viewer;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "cpc_amount", nullable = false)
    private BigDecimal cpcAmount;

    @Column(name = "reputation_score_snapshot", nullable = false)
    private BigDecimal reputationScoreSnapshot;

    @Column(name = "is_charged", nullable = false)
    @Builder.Default
    private Boolean isCharged = true;

    @Column(name = "invalid_reason")
    private String invalidReason;

    @CreationTimestamp
    @Column(name = "clicked_at", updatable = false)
    private ZonedDateTime clickedAt;
}
