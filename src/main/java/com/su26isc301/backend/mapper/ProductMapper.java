package com.su26isc301.backend.mapper;

import com.su26isc301.backend.dto.response.ProductResponse;
import com.su26isc301.backend.entity.Product;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.stream.Collectors;

@Component
public class ProductMapper {

    public ProductResponse mapToProductResponse(Product product) {
        if (product == null) {
            return null;
        }

        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setVendorId(product.getVendor() != null ? product.getVendor().getId() : null);
        response.setVendorName(product.getVendor() != null ? product.getVendor().getShopName() : null);
        response.setCategoryId(product.getCategory() != null ? product.getCategory().getId() : null);
        response.setCategoryName(product.getCategory() != null ? product.getCategory().getName() : null);
        response.setName(product.getName());
        response.setSlug(product.getSlug());
        response.setDescription(product.getDescription());
        response.setStatus(product.getStatus());
        response.setRejectReason(product.getRejectReason());
        response.setAvgRating(product.getAvgRating());
        response.setSoldCount(product.getSoldCount());
        response.setCreatedAt(product.getCreatedAt());

        // Map Media List
        if (product.getMediaList() != null) {
            response.setMediaList(product.getMediaList().stream()
                    .map(m -> ProductResponse.ProductMediaResponse.builder()
                            .id(m.getId())
                            .mediaUrl(m.getMediaUrl())
                            .isMain(m.getIsMain())
                            .mediaType(m.getMediaType())
                            .sortOrder(m.getSortOrder())
                            .build())
                    .collect(Collectors.toList()));
        } else {
            response.setMediaList(new ArrayList<>());
        }

        // Map Attributes List
        if (product.getAttributes() != null) {
            response.setAttributes(product.getAttributes().stream()
                    .map(a -> ProductResponse.ProductAttributeResponse.builder()
                            .id(a.getId())
                            .name(a.getName())
                            .sortOrder(a.getSortOrder())
                            .values(a.getValues() == null ? new ArrayList<>() : a.getValues().stream()
                                    .map(v -> ProductResponse.ProductAttributeValueResponse.builder()
                                            .id(v.getId())
                                            .value(v.getValue())
                                            .imageUrl(v.getImageUrl())
                                            .sortOrder(v.getSortOrder())
                                            .build())
                                    .collect(Collectors.toList()))
                            .build())
                    .collect(Collectors.toList()));
        } else {
            response.setAttributes(new ArrayList<>());
        }

        // Map Variants List
        if (product.getVariants() != null) {
            response.setVariants(product.getVariants().stream()
                    .map(v -> ProductResponse.ProductVariantResponse.builder()
                            .id(v.getId())
                            .sku(v.getSku())
                            .sellerSku(v.getSellerSku())
                            .price(v.getPrice())
                            .stock(v.getStock())
                            .discountPercent(v.getDiscountPercent())
                            .imageUrl(v.getImageUrl())
                            .attributeValueIds(v.getVariantAttributeValues() == null ? new ArrayList<>() :
                                    v.getVariantAttributeValues().stream()
                                            .map(vav -> vav.getAttributeValue().getId())
                                            .collect(Collectors.toList()))
                            .build())
                    .collect(Collectors.toList()));
        } else {
            response.setVariants(new ArrayList<>());
        }

        return response;
    }
}
