package com.su26isc301.backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum VendorCategory {
    THOI_TRANG("Thời trang"),
    MY_PHAM("Mỹ phẩm"),
    GIA_DUNG("Gia dụng"),
    DIEN_TU("Điện tử"),
    ME_VA_BE("Mẹ và bé"),
    SACH("Sách"),
    VAN_PHONG_PHAM("Văn phòng phẩm");

    private final String value;

    VendorCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static VendorCategory fromValue(String value) {
        return Arrays.stream(values())
                .filter(category -> category.value.equalsIgnoreCase(value)
                        || category.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Danh mục bán hàng không hợp lệ"));
    }
}
