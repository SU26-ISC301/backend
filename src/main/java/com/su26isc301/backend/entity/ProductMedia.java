package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "media_url", nullable = false)
    private String mediaUrl;

    @Column(name = "is_main")
    @Builder.Default
    private Boolean isMain = false;

    @Column(name = "media_type")
    @Builder.Default
    private String mediaType = "image";

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}