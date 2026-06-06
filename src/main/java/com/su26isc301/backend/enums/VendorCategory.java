package com.su26isc301.backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum VendorCategory {
    DT_DO_DIEN_TU("dt-do-dien-tu", "Điện thoại & Đồ điện tử"),
    MAY_TINH_VAN_PHONG("may-tinh-van-phong", "Máy tính & Thiết bị Văn phòng"),
    THIET_BI_MANG("thiet-bi-mang", "Thiết bị mạng"),
    TV_GIAI_TRI("tv-giai-tri", "TV & Thiết bị giải trí");

    private final String id;
    private final String value;

    VendorCategory(String id, String value) {
        this.id = id;
        this.value = value;
    }

    public String getId() {
        return id;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static VendorCategory fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Danh mục bán hàng không hợp lệ");
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(category -> category.id.equalsIgnoreCase(normalized)
                        || category.value.equalsIgnoreCase(normalized)
                        || category.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Danh mục bán hàng không hợp lệ"));
    }
}
