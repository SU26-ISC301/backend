package com.su26isc301.backend.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductCreateRequest {
    private Long categoryId;
    private Long brandId;
    private String name;
    private String description;
    private String status;
    private String condition;
    private String originCountry;
    private String warrantyType;

    private Integer parcelWeightG;
    private Integer parcelWidth;
    private Integer parcelLength;
    private Integer parcelHeight;
    private String deliveryMethod;

    private List<ProductMediaRequest> mediaList;
    private List<ProductAttributeRequest> attributes;
    private List<ProductVariantRequest> variants;

    @Data
    public static class ProductMediaRequest {
        private String mediaUrl;
        private Boolean isMain;
        private String mediaType;
        private Integer sortOrder;
    }

    @Data
    public static class ProductAttributeRequest {
        private String name;
        private Integer sortOrder;
        private List<ProductAttributeValueRequest> values;
    }

    @Data
    public static class ProductAttributeValueRequest {
        private String value;
        private String imageUrl;
        private Integer sortOrder;
    }

    @Data
    public static class ProductVariantRequest {
        private String sku;
        private String sellerSku;
        private BigDecimal price;
        private Integer stock;
        private Integer discountPercent;
        private String imageUrl;
        private List<Long> attributeValueIds; // Dùng để map với các ProductAttributeValue
    }
}