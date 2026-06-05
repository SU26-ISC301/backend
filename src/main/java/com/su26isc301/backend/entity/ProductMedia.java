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
    private Boolean isMain = false;

    @Column(name = "media_type")
    private String mediaType = "image"; // Lưu giá trị "image" hoặc "video"

    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}