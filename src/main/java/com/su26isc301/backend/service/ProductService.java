package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.request.ProductCreateRequest;
import com.su26isc301.backend.dto.response.ProductResponse;
import com.su26isc301.backend.entity.*;
import com.su26isc301.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProfileRepository profileRepository;
    private final VendorRepository vendorRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    @Transactional
    public ProductResponse createProduct(String email, ProductCreateRequest request) {
        Profile profile = profileRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin tài khoản người dùng"));

        Vendor vendor = vendorRepository.findByProfile(profile)
                .orElseThrow(() -> new RuntimeException("Tài khoản này chưa đăng ký gian hàng Vendor"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục với ID: " + request.getCategoryId()));

        Brand brand = null;
        if (request.getBrandId() != null) {
            brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thương hiệu với ID: " + request.getBrandId()));
        }

        String uniqueSlug = createUniqueSlug(request.getName());

        Product product = Product.builder()
                .vendor(vendor)
                .category(category)
                .brand(brand)
                .name(request.getName())
                .description(request.getDescription())
                .slug(uniqueSlug)
                .status(request.getStatus() != null ? request.getStatus() : "draft")
                .condition(request.getCondition() != null ? request.getCondition() : "new")
                .originCountry(request.getOriginCountry())
                .warrantyType(request.getWarrantyType())
                .parcelWeightG(request.getParcelWeightG())
                .parcelWidth(request.getParcelWidth())
                .parcelLength(request.getParcelLength())
                .parcelHeight(request.getParcelHeight())
                .deliveryMethod(request.getDeliveryMethod() != null ? request.getDeliveryMethod() : "default")
                .isActive(true)
                .mediaList(new ArrayList<>())
                .attributes(new ArrayList<>())
                .variants(new ArrayList<>())
                .build();

        // 1. Process Media List
        if (request.getMediaList() != null) {
            for (ProductCreateRequest.ProductMediaRequest mediaReq : request.getMediaList()) {
                ProductMedia media = ProductMedia.builder()
                        .product(product)
                        .mediaUrl(mediaReq.getMediaUrl())
                        .isMain(mediaReq.getIsMain() != null ? mediaReq.getIsMain() : false)
                        .mediaType(mediaReq.getMediaType() != null ? mediaReq.getMediaType() : "image")
                        .sortOrder(mediaReq.getSortOrder() != null ? mediaReq.getSortOrder() : 0)
                        .build();
                product.getMediaList().add(media);
            }
        }

        // 2. Process Attributes and keep a flat list of values for variant mapping
        List<ProductAttributeValue> flatValues = new ArrayList<>();
        if (request.getAttributes() != null) {
            for (ProductCreateRequest.ProductAttributeRequest attrReq : request.getAttributes()) {
                ProductAttribute attribute = ProductAttribute.builder()
                        .product(product)
                        .name(attrReq.getName())
                        .sortOrder(attrReq.getSortOrder() != null ? attrReq.getSortOrder() : 0)
                        .values(new ArrayList<>())
                        .build();

                if (attrReq.getValues() != null) {
                    for (ProductCreateRequest.ProductAttributeValueRequest valReq : attrReq.getValues()) {
                        ProductAttributeValue val = ProductAttributeValue.builder()
                                .attribute(attribute)
                                .value(valReq.getValue())
                                .imageUrl(valReq.getImageUrl())
                                .sortOrder(valReq.getSortOrder() != null ? valReq.getSortOrder() : 0)
                                .build();
                        attribute.getValues().add(val);
                        flatValues.add(val);
                    }
                }
                product.getAttributes().add(attribute);
            }
        }

        // 3. Process Variants and link to Attribute Values using request indexes
        if (request.getVariants() != null) {
            for (ProductCreateRequest.ProductVariantRequest varReq : request.getVariants()) {
                ProductVariant variant = ProductVariant.builder()
                        .product(product)
                        .sku(varReq.getSku())
                        .sellerSku(varReq.getSellerSku())
                        .price(varReq.getPrice())
                        .stock(varReq.getStock() != null ? varReq.getStock() : 0)
                        .discountPercent(varReq.getDiscountPercent() != null ? varReq.getDiscountPercent() : 0)
                        .imageUrl(varReq.getImageUrl())
                        .isActive(true)
                        .variantAttributeValues(new ArrayList<>())
                        .build();

                if (varReq.getAttributeValueIds() != null) {
                    for (Long idx : varReq.getAttributeValueIds()) {
                        int index = idx.intValue();
                        if (index >= 0 && index < flatValues.size()) {
                            ProductAttributeValue attrVal = flatValues.get(index);
                            VariantAttributeValue vav = VariantAttributeValue.builder()
                                    .variant(variant)
                                    .attributeValue(attrVal)
                                    .build();
                            variant.getVariantAttributeValues().add(vav);
                        }
                    }
                }
                product.getVariants().add(variant);
            }
        }

        Product savedProduct = productRepository.save(product);
        return mapToProductResponse(savedProduct);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm hoặc sản phẩm đã bị xóa mềm với ID: " + id));
        return mapToProductResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByVendor(Long vendorId) {
        List<Product> products = productRepository.findByVendorIdAndIsActiveTrue(vendorId);
        return products.stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductResponse updateProduct(String email, Long id, ProductCreateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + id));

        // Check ownership
        if (!product.getVendor().getProfile().getEmail().equalsIgnoreCase(email.trim())) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa sản phẩm của gian hàng khác");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục với ID: " + request.getCategoryId()));

        Brand brand = null;
        if (request.getBrandId() != null) {
            brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thương hiệu với ID: " + request.getBrandId()));
        }

        // Regenerate slug if name changed
        if (!product.getName().equalsIgnoreCase(request.getName())) {
            product.setName(request.getName());
            product.setSlug(createUniqueSlug(request.getName()));
        }

        product.setCategory(category);
        product.setBrand(brand);
        product.setDescription(request.getDescription());
        product.setStatus(request.getStatus() != null ? request.getStatus() : "draft");
        product.setCondition(request.getCondition() != null ? request.getCondition() : "new");
        product.setOriginCountry(request.getOriginCountry());
        product.setWarrantyType(request.getWarrantyType());
        product.setParcelWeightG(request.getParcelWeightG());
        product.setParcelWidth(request.getParcelWidth());
        product.setParcelLength(request.getParcelLength());
        product.setParcelHeight(request.getParcelHeight());
        product.setDeliveryMethod(request.getDeliveryMethod() != null ? request.getDeliveryMethod() : "default");

        // Clear existing details
        product.getMediaList().clear();
        product.getAttributes().clear();
        product.getVariants().clear();

        // 1. Process Media List
        if (request.getMediaList() != null) {
            for (ProductCreateRequest.ProductMediaRequest mediaReq : request.getMediaList()) {
                ProductMedia media = ProductMedia.builder()
                        .product(product)
                        .mediaUrl(mediaReq.getMediaUrl())
                        .isMain(mediaReq.getIsMain() != null ? mediaReq.getIsMain() : false)
                        .mediaType(mediaReq.getMediaType() != null ? mediaReq.getMediaType() : "image")
                        .sortOrder(mediaReq.getSortOrder() != null ? mediaReq.getSortOrder() : 0)
                        .build();
                product.getMediaList().add(media);
            }
        }

        // 2. Process Attributes and values
        List<ProductAttributeValue> flatValues = new ArrayList<>();
        if (request.getAttributes() != null) {
            for (ProductCreateRequest.ProductAttributeRequest attrReq : request.getAttributes()) {
                ProductAttribute attribute = ProductAttribute.builder()
                        .product(product)
                        .name(attrReq.getName())
                        .sortOrder(attrReq.getSortOrder() != null ? attrReq.getSortOrder() : 0)
                        .values(new ArrayList<>())
                        .build();

                if (attrReq.getValues() != null) {
                    for (ProductCreateRequest.ProductAttributeValueRequest valReq : attrReq.getValues()) {
                        ProductAttributeValue val = ProductAttributeValue.builder()
                                .attribute(attribute)
                                .value(valReq.getValue())
                                .imageUrl(valReq.getImageUrl())
                                .sortOrder(valReq.getSortOrder() != null ? valReq.getSortOrder() : 0)
                                .build();
                        attribute.getValues().add(val);
                        flatValues.add(val);
                    }
                }
                product.getAttributes().add(attribute);
            }
        }

        // 3. Process Variants
        if (request.getVariants() != null) {
            for (ProductCreateRequest.ProductVariantRequest varReq : request.getVariants()) {
                ProductVariant variant = ProductVariant.builder()
                        .product(product)
                        .sku(varReq.getSku())
                        .sellerSku(varReq.getSellerSku())
                        .price(varReq.getPrice())
                        .stock(varReq.getStock() != null ? varReq.getStock() : 0)
                        .discountPercent(varReq.getDiscountPercent() != null ? varReq.getDiscountPercent() : 0)
                        .imageUrl(varReq.getImageUrl())
                        .isActive(true)
                        .variantAttributeValues(new ArrayList<>())
                        .build();

                if (varReq.getAttributeValueIds() != null) {
                    for (Long idx : varReq.getAttributeValueIds()) {
                        int index = idx.intValue();
                        if (index >= 0 && index < flatValues.size()) {
                            ProductAttributeValue attrVal = flatValues.get(index);
                            VariantAttributeValue vav = VariantAttributeValue.builder()
                                    .variant(variant)
                                    .attributeValue(attrVal)
                                    .build();
                            variant.getVariantAttributeValues().add(vav);
                        }
                    }
                }
                product.getVariants().add(variant);
            }
        }

        Product savedProduct = productRepository.save(product);
        return mapToProductResponse(savedProduct);
    }

    @Transactional
    public void deleteProduct(String email, Long id, boolean hardDelete) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + id));

        // Check ownership
        if (!product.getVendor().getProfile().getEmail().equalsIgnoreCase(email.trim())) {
            throw new RuntimeException("Bạn không có quyền xóa sản phẩm của gian hàng khác");
        }

        if (hardDelete) {
            productRepository.delete(product);
        } else {
            product.setIsActive(false);
            if (product.getVariants() != null) {
                for (ProductVariant variant : product.getVariants()) {
                    variant.setIsActive(false);
                }
            }
            productRepository.save(product);
        }
    }

    private ProductResponse mapToProductResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setVendorId(product.getVendor().getId());
        response.setVendorName(product.getVendor().getShopName());
        response.setCategoryId(product.getCategory().getId());
        response.setCategoryName(product.getCategory().getName());
        response.setName(product.getName());
        response.setSlug(product.getSlug());
        response.setDescription(product.getDescription());
        response.setStatus(product.getStatus());
        response.setAvgRating(product.getAvgRating());
        response.setSoldCount(product.getSoldCount());
        response.setCreatedAt(product.getCreatedAt());

        // Map Media
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
        }

        // Map Attributes
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
        }

        // Map Variants
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
        }

        return response;
    }

    private String createUniqueSlug(String name) {
        String baseSlug = generateSlug(name);
        if (baseSlug.isBlank()) {
            baseSlug = "product";
        }
        String uniqueSlug = baseSlug;
        int counter = 1;
        while (productRepository.existsBySlug(uniqueSlug)) {
            uniqueSlug = baseSlug + "-" + counter;
            counter++;
        }
        return uniqueSlug;
    }

    private String generateSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String noDiacritics = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return noDiacritics.toLowerCase()
                .replaceAll("đ", "d")
                .replaceAll("Đ", "d")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }
}
