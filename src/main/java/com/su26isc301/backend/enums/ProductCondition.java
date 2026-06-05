package com.su26isc301.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductCondition {
    NEW("new"),
    USED("used");

    private final String value;
}
