package com.su26isc301.backend.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private Long vendorId;
    private String vendorName;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String slug;
    private String description;
    private String status;
    private BigDecimal avgRating;
    private Integer soldCount;
    private ZonedDateTime createdAt;
    private List<ProductMediaResponse> mediaList;
    private List<ProductAttributeResponse> attributes;
    private List<ProductVariantResponse> variants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductMediaResponse {
        private Long id;
        private String mediaUrl;
        private Boolean isMain;
        private String mediaType;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductAttributeResponse {
        private Long id;
        private String name;
        private Integer sortOrder;
        private List<ProductAttributeValueResponse> values;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductAttributeValueResponse {
        private Long id;
        private String value;
        private String imageUrl;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductVariantResponse {
        private Long id;
        private String sku;
        private String sellerSku;
        private BigDecimal price;
        private Integer stock;
        private Integer discountPercent;
        private String imageUrl;
        private List<Long> attributeValueIds;
    }
}