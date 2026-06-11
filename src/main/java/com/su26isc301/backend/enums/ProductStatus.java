package com.su26isc301.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductStatus {
    DRAFT("draft"),
    PENDING("pending"),
    ACTIVE("active"),
    REJECTED("rejected"),
    WARNING("warning"),
    INACTIVE("inactive");

    private final String value;
}
