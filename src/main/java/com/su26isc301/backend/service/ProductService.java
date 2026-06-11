package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.request.ProductCreateRequest;
import com.su26isc301.backend.dto.response.ProductResponse;
import com.su26isc301.backend.entity.*;
import com.su26isc301.backend.enums.ProductCondition;
import com.su26isc301.backend.enums.ProductStatus;
import com.su26isc301.backend.exception.ForbiddenAccessException;
import com.su26isc301.backend.exception.ResourceNotFoundException;
import com.su26isc301.backend.mapper.ProductMapper;
import com.su26isc301.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.ZonedDateTime;
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
    private final ProductMapper productMapper;
    private final SubscriptionService subscriptionService;
    private final VendorSubscriptionPlanRepository subscriptionPlanRepository;

    @Transactional
    public ProductResponse createProduct(String email, ProductCreateRequest request) {
        Profile profile = profileRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản với email: " + email));

        Vendor vendor = vendorRepository.findByProfile(profile)
                .orElseThrow(() -> new ForbiddenAccessException("Tài khoản này chưa đăng ký gian hàng Vendor"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với ID: " + request.getCategoryId()));

        Brand brand = null;
        if (request.getBrandId() != null) {
            brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu với ID: " + request.getBrandId()));
        }

        String uniqueSlug = createUniqueSlug(request.getName());

        String requestedStatus = request.getStatus() != null ? request.getStatus().trim().toLowerCase() : ProductStatus.DRAFT.getValue();
        if (!requestedStatus.equals(ProductStatus.DRAFT.getValue()) && !requestedStatus.equals(ProductStatus.PENDING.getValue())) {
            requestedStatus = ProductStatus.PENDING.getValue();
        }
        boolean consumesSlot = requestedStatus.equals(ProductStatus.PENDING.getValue());
        if (consumesSlot && !subscriptionService.canPostProduct(vendor.getId())) {
            throw new ForbiddenAccessException("Đã hết lượt đăng tin trong tháng. Vui lòng nâng cấp gói để tiếp tục đăng sản phẩm.");
        }

        Product product = Product.builder()
                .vendor(vendor)
                .category(category)
                .brand(brand)
                .name(request.getName())
                .description(request.getDescription())
                .slug(uniqueSlug)
                .status(requestedStatus)
                .condition(request.getCondition() != null ? request.getCondition() : ProductCondition.NEW.getValue())
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

        // Populate relationships using DRY synchronization methods
        syncMediaList(product, request.getMediaList());
        List<ProductAttributeValue> flatValues = syncAttributes(product, request.getAttributes());
        syncVariants(product, request.getVariants(), flatValues);

        Product savedProduct = productRepository.save(product);
        if (consumesSlot) {
            subscriptionService.consumeOneSlot(vendor.getId());
        }
        return mapToProductResponseWithVendorPlan(savedProduct);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm hoặc sản phẩm đã bị xóa với ID: " + id));
        return mapToProductResponseWithVendorPlan(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByVendor(Long vendorId) {
        List<Product> products = productRepository.findByVendorIdAndIsActiveTrue(vendorId);
        return products.stream()
                .map(this::mapToProductResponseWithVendorPlan)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsForAdmin(String status) {
        String normalizedStatus = status != null ? status.trim().toLowerCase() : "";
        List<Product> products = normalizedStatus.isBlank()
                ? productRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                : productRepository.findByStatusIgnoreCaseAndIsActiveTrueOrderByCreatedAtDesc(normalizedStatus);

        return products.stream()
                .map(this::mapToProductResponseWithVendorPlan)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getPublicActiveProducts() {
        return productRepository
                .findByStatusIgnoreCaseAndIsActiveTrueOrderByCreatedAtDesc(ProductStatus.ACTIVE.getValue())
                .stream()
                .map(this::mapToProductResponseWithVendorPlan)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductResponse updateProduct(String email, Long id, ProductCreateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id));

        // Check ownership
        if (!product.getVendor().getProfile().getEmail().equalsIgnoreCase(email.trim())) {
            throw new ForbiddenAccessException("Bạn không có quyền chỉnh sửa sản phẩm của gian hàng khác");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với ID: " + request.getCategoryId()));

        Brand brand = null;
        if (request.getBrandId() != null) {
            brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu với ID: " + request.getBrandId()));
        }

        // Regenerate slug if name changed
        if (!product.getName().equalsIgnoreCase(request.getName())) {
            product.setName(request.getName());
            product.setSlug(createUniqueSlug(request.getName()));
        }

        product.setCategory(category);
        product.setBrand(brand);
        product.setDescription(request.getDescription());

        String currentStatus = product.getStatus() != null ? product.getStatus().trim().toLowerCase() : ProductStatus.DRAFT.getValue();
        String requestedStatus = request.getStatus() != null ? request.getStatus().trim().toLowerCase() : ProductStatus.DRAFT.getValue();
        boolean consumesSlot = currentStatus.equals(ProductStatus.DRAFT.getValue())
                && requestedStatus.equals(ProductStatus.PENDING.getValue());
        if (consumesSlot && !subscriptionService.canPostProduct(product.getVendor().getId())) {
            throw new ForbiddenAccessException("Đã hết lượt đăng tin trong tháng. Vui lòng nâng cấp gói để tiếp tục đăng sản phẩm.");
        }

        if (currentStatus.equals(ProductStatus.ACTIVE.getValue()) || currentStatus.equals(ProductStatus.INACTIVE.getValue())) {
            if (requestedStatus.equals("inactive")) {
                product.setStatus(ProductStatus.INACTIVE.getValue());
            } else if (requestedStatus.equals("draft")) {
                product.setStatus(ProductStatus.DRAFT.getValue());
                product.setRejectReason(null);
            } else if (requestedStatus.equals("pending")) {
                product.setStatus(ProductStatus.PENDING.getValue());
                product.setRejectReason(null);
            } else {
                product.setStatus(ProductStatus.ACTIVE.getValue());
            }
        } else {
            if (requestedStatus.equals("draft")) {
                product.setStatus(ProductStatus.DRAFT.getValue());
            } else {
                product.setStatus(ProductStatus.PENDING.getValue());
            }
            product.setRejectReason(null);
        }
        product.setCondition(request.getCondition() != null ? request.getCondition() : ProductCondition.NEW.getValue());
        product.setOriginCountry(request.getOriginCountry());
        product.setWarrantyType(request.getWarrantyType());
        product.setParcelWeightG(request.getParcelWeightG());
        product.setParcelWidth(request.getParcelWidth());
        product.setParcelLength(request.getParcelLength());
        product.setParcelHeight(request.getParcelHeight());
        product.setDeliveryMethod(request.getDeliveryMethod() != null ? request.getDeliveryMethod() : "default");

        // Sync lists to prevent Hibernate clear-and-insert anti-pattern
        syncMediaList(product, request.getMediaList());
        List<ProductAttributeValue> flatValues = syncAttributes(product, request.getAttributes());
        syncVariants(product, request.getVariants(), flatValues);

        Product savedProduct = productRepository.save(product);
        if (consumesSlot) {
            subscriptionService.consumeOneSlot(product.getVendor().getId());
        }
        return mapToProductResponseWithVendorPlan(savedProduct);
    }

    @Transactional
    public void deleteProduct(String email, Long id, boolean hardDelete) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id));

        // Check ownership
        if (!product.getVendor().getProfile().getEmail().equalsIgnoreCase(email.trim())) {
            throw new ForbiddenAccessException("Bạn không có quyền xóa sản phẩm của gian hàng khác");
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

    @Transactional
    public ProductResponse approveProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id));
        product.setStatus(ProductStatus.ACTIVE.getValue());
        product.setRejectReason(null);
        Product saved = productRepository.save(product);
        return mapToProductResponseWithVendorPlan(saved);
    }

    @Transactional
    public ProductResponse rejectProduct(Long id, String reason) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id));
        product.setStatus(ProductStatus.REJECTED.getValue());
        product.setRejectReason(reason);
        Product saved = productRepository.save(product);
        return mapToProductResponseWithVendorPlan(saved);
    }

    @Transactional
    public ProductResponse warnProduct(Long id, String reason) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id));
        product.setStatus(ProductStatus.WARNING.getValue());
        product.setRejectReason(reason);
        Product saved = productRepository.save(product);
        return mapToProductResponseWithVendorPlan(saved);
    }

    // --- Private Helper Methods (DRY Collection Synchronization & Matching) ---

    private ProductResponse mapToProductResponseWithVendorPlan(Product product) {
        ProductResponse response = productMapper.mapToProductResponse(product);
        if (response == null || product == null || product.getVendor() == null) {
            return response;
        }

        subscriptionPlanRepository.findByVendorIdAndIsActiveTrue(product.getVendor().getId())
                .ifPresent(plan -> {
                    boolean activeNow = plan.getExpiresAt() == null || ZonedDateTime.now().isBefore(plan.getExpiresAt());
                    String planType = plan.getPlanType() != null ? plan.getPlanType().trim().toLowerCase() : SubscriptionService.PLAN_FREE;
                    response.setVendorPlanType(planType);
                    response.setPremiumHighlighted(activeNow && SubscriptionService.PLAN_PREMIUM.equals(planType));
                });

        if (response.getVendorPlanType() == null) {
            response.setVendorPlanType(SubscriptionService.PLAN_FREE);
        }
        if (response.getPremiumHighlighted() == null) {
            response.setPremiumHighlighted(false);
        }

        return response;
    }

    private void syncMediaList(Product product, List<ProductCreateRequest.ProductMediaRequest> mediaRequests) {
        if (mediaRequests == null) {
            product.getMediaList().clear();
            return;
        }

        java.util.Map<String, ProductCreateRequest.ProductMediaRequest> reqMap = mediaRequests.stream()
                .collect(Collectors.toMap(
                        ProductCreateRequest.ProductMediaRequest::getMediaUrl,
                        r -> r,
                        (r1, r2) -> r1
                ));

        // Remove media that are no longer in the request
        product.getMediaList().removeIf(m -> !reqMap.containsKey(m.getMediaUrl()));

        // Update existing or add new
        for (ProductCreateRequest.ProductMediaRequest req : mediaRequests) {
            Optional<ProductMedia> existing = product.getMediaList().stream()
                    .filter(m -> m.getMediaUrl().equals(req.getMediaUrl()))
                    .findFirst();

            if (existing.isPresent()) {
                ProductMedia m = existing.get();
                m.setIsMain(req.getIsMain() != null ? req.getIsMain() : false);
                m.setMediaType(req.getMediaType() != null ? req.getMediaType() : "image");
                m.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
            } else {
                ProductMedia newMedia = ProductMedia.builder()
                        .product(product)
                        .mediaUrl(req.getMediaUrl())
                        .isMain(req.getIsMain() != null ? req.getIsMain() : false)
                        .mediaType(req.getMediaType() != null ? req.getMediaType() : "image")
                        .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                        .build();
                product.getMediaList().add(newMedia);
            }
        }
    }

    private List<ProductAttributeValue> syncAttributes(Product product, List<ProductCreateRequest.ProductAttributeRequest> attrRequests) {
        List<ProductAttributeValue> flatValues = new ArrayList<>();
        if (attrRequests == null) {
            product.getAttributes().clear();
            return flatValues;
        }

        java.util.Map<String, ProductCreateRequest.ProductAttributeRequest> reqMap = attrRequests.stream()
                .collect(Collectors.toMap(
                        ProductCreateRequest.ProductAttributeRequest::getName,
                        r -> r,
                        (r1, r2) -> r1
                ));

        // Remove attributes that are no longer in the request
        product.getAttributes().removeIf(a -> !reqMap.containsKey(a.getName()));

        // Process each attribute request
        for (ProductCreateRequest.ProductAttributeRequest attrReq : attrRequests) {
            ProductAttribute attribute = product.getAttributes().stream()
                    .filter(a -> a.getName().equals(attrReq.getName()))
                    .findFirst()
                    .orElse(null);

            if (attribute == null) {
                attribute = ProductAttribute.builder()
                        .product(product)
                        .name(attrReq.getName())
                        .sortOrder(attrReq.getSortOrder() != null ? attrReq.getSortOrder() : 0)
                        .values(new ArrayList<>())
                        .build();
                product.getAttributes().add(attribute);
            } else {
                attribute.setSortOrder(attrReq.getSortOrder() != null ? attrReq.getSortOrder() : 0);
            }

            // Sync nested values list
            syncAttributeValues(attribute, attrReq.getValues(), flatValues);
        }

        return flatValues;
    }

    private void syncAttributeValues(ProductAttribute attribute, List<ProductCreateRequest.ProductAttributeValueRequest> valRequests, List<ProductAttributeValue> flatValues) {
        if (valRequests == null) {
            attribute.getValues().clear();
            return;
        }

        java.util.Map<String, ProductCreateRequest.ProductAttributeValueRequest> reqMap = valRequests.stream()
                .collect(Collectors.toMap(
                        ProductCreateRequest.ProductAttributeValueRequest::getValue,
                        r -> r,
                        (r1, r2) -> r1
                ));

        // Remove values no longer in request
        attribute.getValues().removeIf(v -> !reqMap.containsKey(v.getValue()));

        // Update or add values
        for (ProductCreateRequest.ProductAttributeValueRequest valReq : valRequests) {
            ProductAttributeValue valueEntity = attribute.getValues().stream()
                    .filter(v -> v.getValue().equals(valReq.getValue()))
                    .findFirst()
                    .orElse(null);

            if (valueEntity == null) {
                valueEntity = ProductAttributeValue.builder()
                        .attribute(attribute)
                        .value(valReq.getValue())
                        .imageUrl(valReq.getImageUrl())
                        .sortOrder(valReq.getSortOrder() != null ? valReq.getSortOrder() : 0)
                        .build();
                attribute.getValues().add(valueEntity);
            } else {
                valueEntity.setImageUrl(valReq.getImageUrl());
                valueEntity.setSortOrder(valReq.getSortOrder() != null ? valReq.getSortOrder() : 0);
            }
            flatValues.add(valueEntity);
        }
    }

    private void syncVariants(Product product, List<ProductCreateRequest.ProductVariantRequest> varRequests, List<ProductAttributeValue> flatValues) {
        if (varRequests == null) {
            product.getVariants().clear();
            return;
        }

        java.util.Map<String, ProductCreateRequest.ProductVariantRequest> reqMap = varRequests.stream()
                .collect(Collectors.toMap(
                        ProductCreateRequest.ProductVariantRequest::getSku,
                        r -> r,
                        (r1, r2) -> r1
                ));

        // Remove variants no longer in request
        product.getVariants().removeIf(v -> !reqMap.containsKey(v.getSku()));

        // Update or add variants
        for (ProductCreateRequest.ProductVariantRequest varReq : varRequests) {
            ProductVariant variant = product.getVariants().stream()
                    .filter(v -> v.getSku().equals(varReq.getSku()))
                    .findFirst()
                    .orElse(null);

            if (variant == null) {
                variant = ProductVariant.builder()
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
                product.getVariants().add(variant);
            } else {
                variant.setSellerSku(varReq.getSellerSku());
                variant.setPrice(varReq.getPrice());
                variant.setStock(varReq.getStock() != null ? varReq.getStock() : 0);
                variant.setDiscountPercent(varReq.getDiscountPercent() != null ? varReq.getDiscountPercent() : 0);
                variant.setImageUrl(varReq.getImageUrl());
                variant.setIsActive(true);
            }

            // Sync variant attributes mapping (rebuild based on request indexes)
            variant.getVariantAttributeValues().clear();
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
        }
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
