package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "promotion_impressions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionImpression {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    private PostPromotion promotion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viewer_id")
    private Profile viewer;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "surface", nullable = false)
    private String surface;

    @CreationTimestamp
    @Column(name = "viewed_at", updatable = false)
    private ZonedDateTime viewedAt;
}
