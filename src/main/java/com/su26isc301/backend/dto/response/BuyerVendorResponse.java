package com.su26isc301.backend.dto.response;

public record BuyerVendorResponse(
        Long id,
        String shopName,
        String logoUrl,
        String description
) {
}
